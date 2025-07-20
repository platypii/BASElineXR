#!/bin/bash

RES=0.5
IMG_RES=0.5

mkdir -p build

# Assemble images from tiles
gdalbuildvrt -tr $IMG_RES $IMG_RES build/eiger_rgb_${IMG_RES}m.vrt images/*.tif

# Convert to JPG for VR
gdal_translate -of JPEG build/eiger_rgb_${IMG_RES}m.vrt build/eiger_rgb_${IMG_RES}m.jpg

# Assemble dems from tiles
rm -f build/eiger_dem_${RES}m.vrt build/eiger_dem_${RES}m.tif
gdalbuildvrt -tr $RES $RES build/eiger_dem_${RES}m.vrt dem/*.tif
gdalwarp -tr $RES $RES -r cubic build/eiger_dem_${RES}m.vrt build/eiger_dem_${RES}m.tif

# Raster to mesh (Wavefront OBJ) and point the MTL to the JPEG
python dem2obj.py \
    build/eiger_dem_${RES}m.tif \
    build/eiger_rgb_${IMG_RES}m.jpg \
    build/eiger_terrain_${RES}m.obj
# This writes build/eiger_terrain_2m.obj + build/eiger_terrain_2m.mtl

# npm install -g obj2gltf
obj2gltf \
    -i build/eiger_terrain_${RES}m.obj \
    -o build/eiger_terrain_${RES}m.glb \
    --binary \
    --inputUpAxis Z \
    --outputUpAxis Y \

# cleanup
rm build/eiger_dem_${RES}m.vrt
rm build/eiger_rgb_${IMG_RES}m.vrt
rm build/eiger_dem_${RES}m.tif
rm build/eiger_rgb_${IMG_RES}m.jpg
rm build/eiger_terrain_${RES}m.obj
rm build/eiger_terrain_${RES}m.mtl
