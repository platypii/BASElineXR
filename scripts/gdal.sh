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

shopt -s nullglob      # empty globs -> empty array, not literal string

# --------------------------------------------------------------------
# Loop over every DEM tile -------------------------------------------
# --------------------------------------------------------------------
for dem in "$DEM_DIR"/*.tif; do
  dem_base=$(basename "$dem")                     # swissalti3d_2024_2644-1158_0.5_2056_5728.tif
  tile_xy=${dem_base#swissalti3d_*_}              # 2644-1158_0.5_2056_5728.tif
  tile_xy=${tile_xy%%_*}                          # 2644-1158
  tile_x=${tile_xy%%-*}                           # 2644  (used for filenames)

  # ------------------------------------------------------------------
  # Find the best matching ortho image (any year / any GSD) ----------
  # ------------------------------------------------------------------
  imgs=( "$IMG_DIR"/swissimage-dop10_*_"$tile_xy"_*_2056.tif )

  if ((${#imgs[@]} == 0)); then
    echo "‼️  No ortho image for DEM $dem_base (tile $tile_xy)" >&2
    continue
  fi

  # Choose newest year (descending) then finest GSD (ascending)
  img=$(printf '%s\n' "${imgs[@]}" | sort -t'_' -k2,2r -k4,4n | head -n1)

  # Extract bits for logging
  img_base=${img##*/}              # strip path
  img_year=${img_base#swissimage-dop10_}
  img_year=${img_year%%_*}         # first field after prefix
  img_gsd=$(echo "$img_base" | cut -d'_' -f4)

  echo "🟢 tile $tile_xy  DEM:$dem_base  IMG:$img_base (year:$img_year gsd:${img_gsd}m)"

  # ------------------------------------------------------------------
  # Paths for temporaries --------------------------------------------
  # ------------------------------------------------------------------
  dem_tmp="$TMP_DIR/dem_${tile_x}.tif"
  img_tmp="$TMP_DIR/img_${tile_x}.tif"
  jpg_tmp="$TMP_DIR/img_${tile_x}.jpg"
  obj_tmp="$TMP_DIR/mesh_${tile_x}.obj"

  # ------------------------------------------------------------------
  # 1. Resample DEM and imagery to $RES metres -----------------------
  # ------------------------------------------------------------------
  gdalwarp -tr "$RES" "$RES" -r cubic    "$dem" "$dem_tmp"
  gdalwarp -tr "$RES" "$RES" -r bilinear "$img" "$img_tmp"

  # ------------------------------------------------------------------
  # 2. RGB → JPEG ----------------------------------------------------
  # ------------------------------------------------------------------
  gdal_translate -of JPEG -co JPEGQuality=90 "$img_tmp" "$jpg_tmp"

  # ------------------------------------------------------------------
  # 3. Height‑field → OBJ -------------------------------------------
  # ------------------------------------------------------------------
  python dem2obj.py "$dem_tmp" "$jpg_tmp" "$obj_tmp"

  # ------------------------------------------------------------------
  # 4. OBJ → GLB -----------------------------------------------------
  # ------------------------------------------------------------------
  obj2gltf \
    -i "$obj_tmp" \
    -o "$OUT_DIR/tile_${tile_x}.glb" \
    --binary \
    --inputUpAxis Z \
    --outputUpAxis Y \
    --optimize

  # ------------------------------------------------------------------
  # 5. Clean up ------------------------------------------------------
  # ------------------------------------------------------------------
  rm -f "$dem_tmp" "$img_tmp" "$jpg_tmp" "$obj_tmp" "${obj_tmp%.obj}.mtl"
done

shopt -u nullglob
