#!/usr/bin/env bash
#---------------------------------------------------------------------------
# Build a watertight 3‑D mesh from ALL *.las/*.laz tiles in laz/
#---------------------------------------------------------------------------
set -euo pipefail
mkdir -p build
OUT=build/eiger_surface.glb

# ---------------------------------------------------------------------------
# 1. Gather tiles and emit a JSON pipeline
# ---------------------------------------------------------------------------
LAS=()
for f in laz/*.las; do
  [[ -e "$f" ]] && LAS+=("\"$f\"")
done
[[ ${#LAS[@]} -gt 0 ]] || { echo "❌  No LAS found in laz/"; exit 1; }
INPUT=$(IFS=, ; echo "${LAS[*]}")

cat > build/eiger_pipeline.json <<EOF
{
  "pipeline":[
    $INPUT,
    { "type":"filters.merge" },
    { "type":"filters.range",  "limits":"Classification![7:7]" },
    { "type":"filters.normal", "knn":8 },

    { "type":"filters.poisson",
      "depth":12
    },

    { "type":"writers.gltf",
      "filename":"$OUT"
    }
  ]
}
EOF

# ---------------------------------------------------------------------------
# 2. Run PDAL
# ---------------------------------------------------------------------------
echo "⛰️  Meshing ${#LAS[@]} tiles with PDAL …"
pdal pipeline build/eiger_pipeline.json
echo "✅ $OUT ready"

# npm i -g gltfpack
gltfpack -i build/eiger_surface.glb -o build/eiger_final.glb -cc -tc -vp 14 -vn 10 -vt 12 -mm
echo "✅ build/eiger_final.glb ready"
