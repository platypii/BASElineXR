#!/usr/bin/env bash
set -euo pipefail

# --------------------------------------------------------------------
# Parameters ---------------------------------------------------------
# --------------------------------------------------------------------
RES=0.5                # Target ground sampling distance in metres
DEM_DIR=dem            # Folder with SwissALTI3D tiles
IMG_DIR=images         # Folder with swissimage tiles
OUT_DIR=build/tiles    # Final GLB destination
TMP_DIR=build/tmp      # Workspace for intermediate rasters/meshes
# --------------------------------------------------------------------

mkdir -p "$OUT_DIR" "$TMP_DIR"

# Loop over every DEM tile ----------------------------------------------------
for dem in "$DEM_DIR"/*.tif; do
  dem_base=$(basename "$dem")                               # swissalti3d_2024_2641-1158_0.5_2056_5728.tif
  IFS='_' read -r _ year tile_xy _ <<< "$dem_base"          # year=2024, tile_xy=2641-1158
  tile_id="${year}_${tile_xy%%-*}"                          # 2024_2641

  # Expected matching ortho‑image name -------------------------------
  img="$IMG_DIR/swissimage-dop10_${year}_${tile_xy}_0.1_2056.tif"
  if [[ ! -f "$img" ]]; then
    echo "No matching image tile for $dem" >&2
    continue
  fi

  # Paths for temporaries --------------------------------------------
  dem_tmp="$TMP_DIR/dem_${tile_id}.tif"
  img_tmp="$TMP_DIR/img_${tile_id}.tif"
  jpg_tmp="$TMP_DIR/img_${tile_id}.jpg"
  obj_tmp="$TMP_DIR/mesh_${tile_id}.obj"

  # ------------------------------------------------------------------
  # 1. Resample DEM and imagery to a common 0.5 m grid  --------------
  # ------------------------------------------------------------------
  gdalwarp -tr "$RES" "$RES" -r cubic    "$dem" "$dem_tmp"
  gdalwarp -tr "$RES" "$RES" -r bilinear "$img" "$img_tmp"

  # ------------------------------------------------------------------
  # 2. Convert RGB to high‑quality JPEG ------------------------------
  # ------------------------------------------------------------------
  gdal_translate -of JPEG -co JPEGQuality=90 "$img_tmp" "$jpg_tmp"

  # ------------------------------------------------------------------
  # 3. Height‑field → OBJ -------------------------------------------
  # ------------------------------------------------------------------
  python dem2obj.py "$dem_tmp" "$jpg_tmp" "$obj_tmp"

  # ------------------------------------------------------------------
  # 4. OBJ → binary GLB ---------------------------------------------
  # ------------------------------------------------------------------
  obj2gltf \
    -i "$obj_tmp" \
    -o "$OUT_DIR/tile_${tile_id}.glb" \
    --binary \
    --inputUpAxis Z \
    --outputUpAxis Y \
    --optimize

  # ------------------------------------------------------------------
  # 5. Clean up clutter (optional) -----------------------------------
  # ------------------------------------------------------------------
  rm -f "$dem_tmp" "$img_tmp" "$jpg_tmp" "$obj_tmp" "${obj_tmp%.obj}.mtl"
done
