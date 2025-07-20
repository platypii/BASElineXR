#!/usr/bin/env python3
"""
Convert a DEM + texture into a simplified Wavefront OBJ
using Delatin (adaptive Delaunay triangulation).

Usage:
    python dem_to_obj.py dem.tif texture.jpg terrain.obj
"""

import sys
from pathlib import Path

from osgeo import gdal, osr
import numpy as np
from pydelatin import Delatin

def main(dem_path, tex_path, obj_path, *, max_err=4.0):
    ds = gdal.Open(dem_path, gdal.GA_ReadOnly)
    if ds is None:
        raise RuntimeError(f'cannot open {dem_path}')
    elev = ds.GetRasterBand(1).ReadAsArray().astype(np.float32)
    print(elev)
    nrows, ncols = elev.shape
    px_size = ds.GetGeoTransform()[1]     # metres / pixel

    print(f'DEM {ncols} × {nrows}   pixel = {px_size:.3f} m')
    print('running delatin …')

    # adaptive triangulation
    tin = Delatin(elev, max_error=max_err)   # <- max error 1 m
    verts, tris = tin.vertices, tin.triangles   # ndarray shapes (N,3) & (M,3)

    # --- drop vertices whose elevation is less than 0
    keep = verts[:, 2] > 0.0                  # boolean mask
    remap = -np.ones(len(verts), dtype=np.int32)
    remap[keep] = np.arange(keep.sum(), dtype=np.int32)

    # keep only triangles made entirely of retained vertices
    tris_keep = keep[tris].all(axis=1)
    tris = remap[tris[tris_keep]]
    verts = verts[keep]

    print(f'output mesh: {len(verts):,} verts, {len(tris):,} tris')

    obj_path = Path(obj_path)
    mtl_path = obj_path.with_suffix('.mtl')

    # write OBJ / MTL
    with open(obj_path, 'w') as obj, open(mtl_path, 'w') as mtl:
        tex_name = Path(tex_path).name
        mtl.write(f'newmtl terrain\nmap_Kd {tex_name}\n')
        obj.write(f'mtllib {mtl_path.name}\nusemtl terrain\n')

        # vertices + UVs --- VR coord system:  X=north/south, Y=east/west, Z=up/down
        for i, (col, row, elev) in enumerate(verts, start=1):
            x = col * px_size
            y = row * px_size
            z = elev # up
            obj.write(f'v {x:.3f} {y:.3f} {z:.3f}\n')

            u = col / (ncols - 1)
            v = row / (nrows - 1)      # image origin is top-left
            obj.write(f'vt {u:.6f} {v:.6f}\n')

        # faces (Delatin already gives triangle indices)
        for a, b, c in tris:
            a += 1; b += 1; c += 1         # OBJ is 1-based
            obj.write(f'f {a}/{a} {b}/{b} {c}/{c}\n')

    print('wrote', obj_path, 'and', mtl_path)

    # Print origin lat/lon
    gt  = ds.GetGeoTransform()          # (x0, px_w, rot, y0, rot, -px_h)
    # Use bottom-left corner instead of top-left to match VR coordinate system
    x0 = gt[0]                          # left edge (same)
    y0 = gt[3] + (nrows * gt[5])        # bottom edge (gt[3] + nrows * -px_h)

    src = osr.SpatialReference()
    src.ImportFromWkt(ds.GetProjection())
    dst = osr.SpatialReference()
    dst.ImportFromEPSG(4326)            # WGS-84 (lat/lon)
    tx  = osr.CoordinateTransformation(src, dst)

    lat, lon, _ = tx.TransformPoint(x0, y0)
    # 8 decimals ≈ 1 mm at mid-latitudes – plenty for VR alignment
    print(f'\norigin lat,lon = {lat:.8f}, {lon:.8f}\n')

if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
