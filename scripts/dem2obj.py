#!/usr/bin/env python3
"""
Convert a DEM (band-1 GeoTIFF) plus a matching RGB image
into a textured Wavefront OBJ.

Usage:
    python dem_to_obj.py dem.tif texture.jpg terrain.obj
"""

import sys, math
from osgeo import gdal
import numpy as np
from pathlib import Path

def main(dem_path, tex_path, obj_path):
    ds = gdal.Open(dem_path, gdal.GA_ReadOnly)
    if ds is None:
        raise RuntimeError(f'cannot open {dem_path}')

    band = ds.GetRasterBand(1)
    elev = band.ReadAsArray().astype(np.float32)
    nrows, ncols = elev.shape
    gt = ds.GetGeoTransform()
    px_size = gt[1]  # 0.5 m for swissALTI3D
    print(f'DEM size {ncols} x {nrows}  px,  pixel = {px_size} m')

    obj_path = Path(obj_path)
    mtl_path = obj_path.with_suffix('.mtl')

    with open(obj_path, 'w') as obj, open(mtl_path, 'w') as mtl:
        # ── write material file ────────────────────────────────
        tex_name = Path(tex_path).name
        mtl.write('newmtl terrain\nmap_Kd ' + tex_name + '\n')
        obj.write(f'mtllib {mtl_path.name}\nusemtl terrain\n')

        # ── vertices & UVs ─────────────────────────────────────
        idx = 1
        for y in range(nrows):
            v = 1 - y / (nrows - 1)           # texture v-coord
            for x in range(ncols):
                u = x / (ncols - 1)           # texture u-coord
                z = elev[y, x]
                obj.write(f'v {x*px_size:.3f} {z:.3f} {y*px_size:.3f}\n')
                obj.write(f'vt {u:.6f} {v:.6f}\n')
                idx += 1

        # ── faces (2 tris per DEM cell) ───────────────────────
        def vid(x, y):      # 1-based OBJ index
            return y * ncols + x + 1

        for y in range(nrows - 1):
            for x in range(ncols - 1):
                v0 = vid(x,   y)
                v1 = vid(x+1, y)
                v2 = vid(x,   y+1)
                v3 = vid(x+1, y+1)
                # face v/t indices are identical
                obj.write(f'f {v0}/{v0} {v2}/{v2} {v1}/{v1}\n')
                obj.write(f'f {v1}/{v1} {v2}/{v2} {v3}/{v3}\n')

    print('wrote', obj_path, 'and', mtl_path)

if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
