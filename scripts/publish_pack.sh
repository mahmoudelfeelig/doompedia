#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PIPELINE_SRC="$ROOT_DIR/data-pipeline/src"

PACK_DIR="${PACK_DIR:-$ROOT_DIR/data/out/en-1m/pack-v1}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/data/site/packs/en-core-1m/v1}"
BASE_URL="${BASE_URL:-}"
LATEST_POINTER="${LATEST_POINTER:-$ROOT_DIR/data/site/packs/en-core-1m/latest.json}"

PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.publish_pack \
  --pack-dir "$PACK_DIR" \
  --output-dir "$OUTPUT_DIR" \
  --base-url "$BASE_URL" \
  --latest-pointer "$LATEST_POINTER" \
  --clean

echo "Published pack to: $OUTPUT_DIR"
