#!/usr/bin/env python3
"""
Merge multiple DEM tiles and export as OBJ mesh.
"""

import sys
import argparse
from pathlib import Path
import numpy as np

try:
    import rasterio
    from rasterio.merge import merge
    from rasterio.crs import CRS
    from rasterio.warp import transform
except ImportError:
    print("Error: rasterio required. Install: pip install rasterio")
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description='Merge DEM tiles to OBJ')
    parser.add_argument('tiles', nargs='+', help='Input DEM GeoTIFF files')
    parser.add_argument('-o', '--output', required=True, help='Output OBJ file')
    parser.add_argument('--resolution', type=float, default=5.0, 
                        help='Target resolution in meters (default: 5)')
    args = parser.parse_args()
    
    print(f'Merging {len(args.tiles)} tiles...')
    datasets = [rasterio.open(f) for f in args.tiles]
    mosaic, mosaic_transform = merge(datasets)
    crs = datasets[0].crs
    for ds in datasets:
        ds.close()
    
    elev = mosaic[0].astype(np.float32)
    native_res = abs(mosaic_transform.a)
    print(f'Merged shape: {elev.shape}, native resolution: {native_res}m')
    
    # Downsample
    downsample = max(1, int(args.resolution / native_res))
    if downsample > 1:
        elev = elev[::downsample, ::downsample]
        print(f'Downsampled to: {elev.shape} at {native_res * downsample:.1f}m')
    
    px_size = native_res * downsample
    nrows, ncols = elev.shape
    
    # Calculate origin (bottom-left)
    x_min = mosaic_transform.c
    y_max = mosaic_transform.f
    y_min = y_max + (nrows * downsample) * mosaic_transform.e
    x_max = x_min + (ncols * downsample) * mosaic_transform.a
    
    dst_crs = CRS.from_epsg(4326)
    origin_lngs, origin_lats = transform(crs, dst_crs, [x_min], [y_min])
    center_lngs, center_lats = transform(crs, dst_crs, [(x_min+x_max)/2], [(y_min+y_max)/2])
    
    # Replace nodata
    elev = np.nan_to_num(elev, nan=0.0)
    nodata_mask = elev < -99999
    if nodata_mask.any():
        # Fill nodata with minimum valid elevation
        valid_elev = elev[~nodata_mask]
        if len(valid_elev) > 0:
            elev[nodata_mask] = np.min(valid_elev)
        else:
            elev[nodata_mask] = 0
    
    print(f'Elevation range: {np.min(elev):.0f}m to {np.max(elev):.0f}m')
    print(f'Generating mesh: {ncols}x{nrows} = {ncols*nrows:,} vertices')
    
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_path, 'w') as obj:
        obj.write(f'# Merged terrain from {len(args.tiles)} tiles\n')
        obj.write(f'# Origin: {origin_lats[0]:.6f}, {origin_lngs[0]:.6f}\n')
        obj.write(f'# Center: {center_lats[0]:.6f}, {center_lngs[0]:.6f}\n')
        obj.write(f'# Resolution: {px_size:.1f}m\n')
        
        # Write vertices
        for row in range(nrows):
            for col in range(ncols):
                x = col * px_size
                y = (nrows - 1 - row) * px_size
                z = elev[row, col]
                obj.write(f'v {x:.2f} {y:.2f} {z:.2f}\n')
        
        # Write faces
        for row in range(nrows - 1):
            for col in range(ncols - 1):
                i = row * ncols + col + 1  # 1-indexed
                obj.write(f'f {i} {i+ncols} {i+ncols+1}\n')
                obj.write(f'f {i} {i+ncols+1} {i+1}\n')
    
    print(f'Output: {output_path}')
    print()
    print('=== Terrain Config ===')
    print(f'Origin (SW): {origin_lats[0]:.6f}, {origin_lngs[0]:.6f}')
    print(f'Center: {center_lats[0]:.6f}, {center_lngs[0]:.6f}')
    print(f'Size: {ncols * px_size:.0f}m x {nrows * px_size:.0f}m')


if __name__ == '__main__':
    main()
