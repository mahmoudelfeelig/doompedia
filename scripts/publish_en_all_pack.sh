#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PIPELINE_SRC="$ROOT_DIR/data-pipeline/src"

PACK_DIR="${PACK_DIR:-$ROOT_DIR/data/out/en-all/pack-v1}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/data/site/packs/en-all-summaries/v1}"
BASE_URL="${BASE_URL:-https://packs.example.invalid/packs/en-all-summaries/v1}"
LATEST_POINTER="${LATEST_POINTER:-$ROOT_DIR/data/site/packs/en-all-summaries/latest.json}"

PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.publish_pack \
  --pack-dir "$PACK_DIR" \
  --output-dir "$OUTPUT_DIR" \
  --base-url "$BASE_URL" \
  --latest-pointer "$LATEST_POINTER" \
  --clean

echo "Published EN-all summaries pack to: $OUTPUT_DIR"
