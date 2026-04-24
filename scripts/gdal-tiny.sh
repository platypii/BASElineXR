#!/bin/bash

set -e

RES=1.0
IMG_RES=2.0

mkdir -p build

# Assemble images from tiles
gdalbuildvrt -tr $IMG_RES $IMG_RES build/eiger_rgb_${IMG_RES}m.vrt images/*.tif

# Convert to JPG for VR
gdal_translate -of JPEG build/eiger_rgb_${IMG_RES}m.vrt build/eiger_rgb_${IMG_RES}m.jpg
cp build/eiger_rgb_${IMG_RES}m.jpg build/eiger_rgb_${IMG_RES}m_precontrast.jpg
# magick mogrify -brightness-contrast 20x20 -modulate 100,120,100 build/eiger_rgb_${IMG_RES}m.jpg
magick mogrify -sigmoidal-contrast 6x30% build/eiger_rgb_${IMG_RES}m.jpg

# Assemble dems from tiles
rm -f build/eiger_dem_${RES}m.vrt build/eiger_dem_${RES}m.tif
gdalbuildvrt -tr $RES $RES build/eiger_dem_${RES}m.vrt dem/*.tif
gdalwarp -tr $RES $RES build/eiger_dem_${RES}m.vrt build/eiger_dem_${RES}m.tif

# Remove small isolated pixel spikes
# gdal_sieve -st 2 -8 -of GTiff \
#     build/eiger_dem_${RES}m.tif build/eiger_dem_${RES}m_sieved.tif

# Raster to mesh (Wavefront OBJ) and point the MTL to the JPEG
python dem2obj.py \
    build/eiger_dem_${RES}m.tif \
    build/eiger_rgb_${IMG_RES}m.jpg \
    build/eiger_terrain_${RES}m.obj \
    29.5
# This writes build/eiger_terrain_2m.obj + build/eiger_terrain_2m.mtl
echo "OBJ written: build/eiger_terrain_${RES}m.obj"

# Add normals
python obj2normals.py build/eiger_terrain_${RES}m.obj build/eiger_terrain_${RES}m_normals.obj

# npm install -g obj2gltf
obj2gltf \
    -i build/eiger_terrain_${RES}m_normals.obj \
    -o build/eiger_terrain_${RES}m.glb \
    --binary \
    --inputUpAxis Z \
    --outputUpAxis Y \

# Texture compression with KHR_texture_basisu extension
# gltf-transform uastc \
#     build/eiger_terrain_${RES}m.glb \
#     build/eiger_terrain_${RES}m_ktx2.glb \
#     --level 2 \
#     --rdo \
#     --zstd 18 \
#     --slots baseColorTexture \
#     --verbose

# quality is 1..255
gltf-transform etc1s \
    build/eiger_terrain_${RES}m.glb \
    build/eiger_terrain_${RES}m_etc1s.glb \
    --quality 100 \
    --filter mitchell \
    --compression 2 \
    --rdo \
    --slots baseColorTexture \
    --jobs 8 \
    --verbose

# cleanup
rm build/eiger_dem_${RES}m.vrt
rm build/eiger_rgb_${IMG_RES}m.vrt
rm build/eiger_dem_${RES}m.tif
# rm build/eiger_rgb_${IMG_RES}m.jpg
rm build/eiger_terrain_${RES}m.obj
rm build/eiger_terrain_${RES}m.mtl
