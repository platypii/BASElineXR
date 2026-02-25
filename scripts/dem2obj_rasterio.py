#!/usr/bin/env python3
"""
Convert a DEM (GeoTIFF) into a simplified Wavefront OBJ
using rasterio and simple grid-based triangulation.

This is a simpler alternative to dem2obj.py that doesn't require 
pydelatin or full GDAL installation.

Usage:
    python dem2obj_rasterio.py dem.tif output.obj [--center-lat LAT --center-lng LNG --radius METERS]
"""

import sys
import argparse
from pathlib import Path
import numpy as np

try:
    import rasterio
    from rasterio.crs import CRS
    from rasterio.warp import transform, calculate_default_transform, reproject, Resampling
    from rasterio.windows import from_bounds
except ImportError:
    print("Error: rasterio is required. Install with: pip install rasterio")
    sys.exit(1)


def latlon_to_utm(lat, lng, utm_crs):
    """Convert lat/lon to UTM coordinates."""
    src_crs = CRS.from_epsg(4326)  # WGS84
    xs, ys = transform(src_crs, utm_crs, [lng], [lat])
    return xs[0], ys[0]


def utm_to_latlon(x, y, utm_crs):
    """Convert UTM coordinates to lat/lon."""
    dst_crs = CRS.from_epsg(4326)  # WGS84
    lngs, lats = transform(utm_crs, dst_crs, [x], [y])
    return lats[0], lngs[0]


def extract_subset(dem_path, center_lat, center_lng, radius, output_path, target_resolution=1.0):
    """
    Extract a subset of the DEM centered on the given coordinates.
    
    Args:
        dem_path: Path to the DEM GeoTIFF
        center_lat: Center latitude
        center_lng: Center longitude
        radius: Radius in meters
        output_path: Path for the output OBJ file
        target_resolution: Target resolution in meters (for downsampling)
    """
    print(f"Opening DEM: {dem_path}")
    with rasterio.open(dem_path) as ds:
        print(f"  Shape: {ds.shape}")
        print(f"  Resolution: {ds.res}")
        print(f"  CRS: {ds.crs}")
        
        # Convert center to the DEM's CRS
        x_center, y_center = latlon_to_utm(center_lat, center_lng, ds.crs)
        print(f"  Center in UTM: {x_center:.2f}, {y_center:.2f}")
        
        # Calculate bounds
        x_min = x_center - radius
        x_max = x_center + radius
        y_min = y_center - radius
        y_max = y_center + radius
        
        # Get window for the subset
        window = from_bounds(x_min, y_min, x_max, y_max, ds.transform)
        print(f"  Window: {window}")
        
        # Read the subset
        # Clamp window to valid range
        row_start = max(0, int(window.row_off))
        row_stop = min(ds.height, int(window.row_off + window.height))
        col_start = max(0, int(window.col_off))
        col_stop = min(ds.width, int(window.col_off + window.width))
        
        print(f"  Reading rows {row_start}:{row_stop}, cols {col_start}:{col_stop}")
        elev = ds.read(1, window=rasterio.windows.Window(
            col_start, row_start, col_stop - col_start, row_stop - row_start
        )).astype(np.float32)
        
        print(f"  Extracted shape: {elev.shape}")
        
        # Get the native resolution
        native_res = ds.res[0]  # meters per pixel
        
        # Calculate how much to downsample
        downsample_factor = max(1, int(target_resolution / native_res))
        if downsample_factor > 1:
            print(f"  Downsampling by factor of {downsample_factor}")
            elev = elev[::downsample_factor, ::downsample_factor]
            print(f"  Downsampled shape: {elev.shape}")
        
        actual_res = native_res * downsample_factor
        
        # Get the actual bounds of what we extracted
        actual_transform = ds.transform * rasterio.Affine.translation(col_start, row_start)
        actual_x_min = actual_transform.c
        actual_y_max = actual_transform.f
        
        # Calculate origin in lat/lon (bottom-left corner for VR coordinate system)
        nrows, ncols = elev.shape
        origin_x = actual_x_min
        origin_y = actual_y_max - (nrows * downsample_factor * native_res)  # bottom edge
        
        origin_lat, origin_lng = utm_to_latlon(origin_x, origin_y, ds.crs)
        center_lat_actual, center_lng_actual = utm_to_latlon(
            origin_x + (ncols * actual_res) / 2,
            origin_y + (nrows * actual_res) / 2,
            ds.crs
        )
        
        print(f"  Origin (bottom-left): {origin_lat:.6f}, {origin_lng:.6f}")
        print(f"  Actual center: {center_lat_actual:.6f}, {center_lng_actual:.6f}")
        print(f"  Elevation range: {np.nanmin(elev):.1f} to {np.nanmax(elev):.1f}")
        
        return elev, actual_res, origin_lat, origin_lng


def generate_obj(elev, px_size, output_path, texture_path=None, skip_threshold=0.0, decimate=1):
    """
    Generate OBJ file from elevation grid.
    
    Args:
        elev: 2D numpy array of elevations
        px_size: Pixel size in meters
        output_path: Output OBJ file path
        texture_path: Optional texture image path
        skip_threshold: Skip vertices below this elevation
        decimate: Additional decimation factor (1 = no decimation)
    """
    if decimate > 1:
        elev = elev[::decimate, ::decimate]
        px_size = px_size * decimate
    
    nrows, ncols = elev.shape
    print(f"Generating mesh: {ncols} x {nrows} = {ncols * nrows:,} vertices")
    
    output_path = Path(output_path)
    mtl_path = output_path.with_suffix('.mtl')
    
    # Replace NaN with 0 or handle missing data
    elev = np.nan_to_num(elev, nan=0.0)
    
    with open(output_path, 'w') as obj:
        # Write MTL reference if texture provided
        if texture_path:
            with open(mtl_path, 'w') as mtl:
                tex_name = Path(texture_path).name
                mtl.write(f'newmtl terrain\nmap_Kd {tex_name}\n')
            obj.write(f'mtllib {mtl_path.name}\nusemtl terrain\n')
        
        # Create vertex index map (for handling skipped vertices)
        vertex_map = np.full((nrows, ncols), -1, dtype=np.int32)
        vertex_idx = 0
        
        # Write vertices - VR coord system: X=east, Y=north, Z=up
        for row in range(nrows):
            for col in range(ncols):
                z = elev[row, col]
                if z <= skip_threshold:
                    continue
                
                # Flip row to match VR coordinate system (origin at bottom-left)
                x = col * px_size
                y = (nrows - 1 - row) * px_size
                
                obj.write(f'v {x:.3f} {y:.3f} {z:.3f}\n')
                
                # UV coordinates
                u = col / (ncols - 1) if ncols > 1 else 0
                v = row / (nrows - 1) if nrows > 1 else 0
                obj.write(f'vt {u:.6f} {v:.6f}\n')
                
                vertex_idx += 1
                vertex_map[row, col] = vertex_idx  # OBJ is 1-indexed
        
        print(f"  Wrote {vertex_idx:,} vertices")
        
        # Write faces (triangles)
        face_count = 0
        for row in range(nrows - 1):
            for col in range(ncols - 1):
                # Get the 4 corner indices
                v00 = vertex_map[row, col]
                v01 = vertex_map[row, col + 1]
                v10 = vertex_map[row + 1, col]
                v11 = vertex_map[row + 1, col + 1]
                
                # Skip if any vertex was filtered out
                if v00 < 0 or v01 < 0 or v10 < 0 or v11 < 0:
                    continue
                
                # Two triangles per quad
                # Triangle 1: v00, v10, v11
                obj.write(f'f {v00}/{v00} {v10}/{v10} {v11}/{v11}\n')
                # Triangle 2: v00, v11, v01
                obj.write(f'f {v00}/{v00} {v11}/{v11} {v01}/{v01}\n')
                face_count += 2
        
        print(f"  Wrote {face_count:,} faces")
    
    print(f"Output: {output_path}")
    if texture_path:
        print(f"MTL: {mtl_path}")


def main():
    parser = argparse.ArgumentParser(description='Convert DEM to OBJ mesh')
    parser.add_argument('dem', help='Input DEM GeoTIFF file')
    parser.add_argument('output', help='Output OBJ file')
    parser.add_argument('--center-lat', type=float, help='Center latitude')
    parser.add_argument('--center-lng', type=float, help='Center longitude')
    parser.add_argument('--radius', type=float, default=2000, help='Radius in meters (default: 2000)')
    parser.add_argument('--resolution', type=float, default=2.0, help='Target resolution in meters (default: 2.0)')
    parser.add_argument('--texture', help='Texture image file')
    parser.add_argument('--decimate', type=int, default=1, help='Additional decimation factor')
    parser.add_argument('--skip-threshold', type=float, default=0.0, help='Skip elevations below this (default: 0)')
    
    args = parser.parse_args()
    
    if args.center_lat and args.center_lng:
        # Extract subset
        elev, px_size, origin_lat, origin_lng = extract_subset(
            args.dem,
            args.center_lat,
            args.center_lng,
            args.radius,
            args.output,
            target_resolution=args.resolution
        )
        print(f"\n=== Terrain Tile Config ===")
        print(f"Origin (for tile config): {origin_lat:.6f}, {origin_lng:.6f}")
    else:
        # Process entire DEM
        print(f"Opening DEM: {args.dem}")
        with rasterio.open(args.dem) as ds:
            elev = ds.read(1).astype(np.float32)
            px_size = ds.res[0]
            print(f"  Shape: {elev.shape}")
            print(f"  Resolution: {px_size}m")
    
    generate_obj(
        elev, 
        px_size, 
        args.output, 
        texture_path=args.texture,
        skip_threshold=args.skip_threshold,
        decimate=args.decimate
    )


if __name__ == '__main__':
    main()
