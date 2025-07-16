#!/bin/bash

mkdir -p build

# Assemble images from tiles
gdalbuildvrt -tr 2 2 build/eiger_rgb_2m.vrt images/*.tif

# Convert to JPG for VR
gdal_translate -of JPEG -co "JPEGQuality=90" build/eiger_rgb_2m.vrt build/eiger_rgb_2m.jpg

# Assemble dems from tiles
gdalbuildvrt -tr 2 2 build/eiger_dem_2m.vrt dem/*.tif
gdalwarp -r cubic build/eiger_dem_2m.vrt build/eiger_dem_2m.tif

# Raster to mesh (Wavefront OBJ) and point the MTL to the JPEG
python dem2obj.py \
    build/eiger_dem_2m.tif \
    build/eiger_rgb_2m.jpg \
    build/eiger_terrain_2m.obj
# This writes build/eiger_terrain_2m.obj + build/eiger_terrain_2m.mtl

# npm install -g obj2gltf
obj2gltf \
    -i build/eiger_terrain_2m.obj \
    -o build/eiger_terrain_2m.glb \
    --binary \
    --inputUpAxis Z \
    --outputUpAxis Y \
    --optimize
