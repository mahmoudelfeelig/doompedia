#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PIPELINE_SRC="$ROOT_DIR/data-pipeline/src"

SOURCE_MANIFEST="${SOURCE_MANIFEST:-$ROOT_DIR/data/site/packs/en-core-1m/v1/manifest.json}"
OUT_ROOT="${OUT_ROOT:-$ROOT_DIR/data/out/focus-packs}"
SITE_ROOT="${SITE_ROOT:-$ROOT_DIR/data/site/packs}"
BASE_URL="${BASE_URL:-https://packs.example.invalid/packs}"

SCI_TARGET="${SCI_TARGET:-250000}"
SCI_SHARD_SIZE="${SCI_SHARD_SIZE:-40000}"
SCI_COMPRESSION="${SCI_COMPRESSION:-gzip}"
SCI_VERSION="${SCI_VERSION:-1}"

HIS_TARGET="${HIS_TARGET:-250000}"
HIS_SHARD_SIZE="${HIS_SHARD_SIZE:-40000}"
HIS_COMPRESSION="${HIS_COMPRESSION:-gzip}"
HIS_VERSION="${HIS_VERSION:-1}"

if [[ ! -f "$SOURCE_MANIFEST" ]]; then
  echo "Source manifest not found: $SOURCE_MANIFEST"
  exit 1
fi

mkdir -p "$OUT_ROOT" "$SITE_ROOT"

build_one_pack() {
  local pack_id="$1"
  local topics="$2"
  local target="$3"
  local shard_size="$4"
  local compression="$5"
  local version="$6"

  local pack_out="$OUT_ROOT/${pack_id}"
  local cards_path="$pack_out/cards.ndjson"
  local pack_dir="$pack_out/pack-v${version}"
  local publish_dir="$SITE_ROOT/${pack_id}/v${version}"
  local latest_pointer="$SITE_ROOT/${pack_id}/latest.json"
  local base_url="$BASE_URL/${pack_id}/v${version}"

  mkdir -p "$pack_out" "$pack_dir" "$publish_dir"

  echo "Building subset cards for ${pack_id} (${target} target)..."
  PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.build_topic_subset \
    --source-manifest "$SOURCE_MANIFEST" \
    --output-ndjson "$cards_path" \
    --language "en" \
    --allowed-topics "$topics" \
    --target "$target"

  local actual_count
  actual_count=$(wc -l < "$cards_path" | tr -d '[:space:]')
  if [[ "$actual_count" -eq 0 ]]; then
    echo "No cards generated for ${pack_id}. Check topic filters and source manifest."
    exit 1
  fi

  echo "Sharding/building ${pack_id} pack (${actual_count} records)..."
  PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.build_pack \
    --input "$cards_path" \
    --output "$pack_dir" \
    --pack-id "$pack_id" \
    --language "en" \
    --max-records "$actual_count" \
    --shard-size "$shard_size" \
    --version "$version" \
    --compression "$compression"

  echo "Publishing ${pack_id} to site folder..."
  PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.publish_pack \
    --pack-dir "$pack_dir" \
    --output-dir "$publish_dir" \
    --base-url "$base_url" \
    --latest-pointer "$latest_pointer" \
    --clean

  echo "Done: ${pack_id} -> ${publish_dir}"
}

build_one_pack "en-science-250k" "science,technology,health,environment" "$SCI_TARGET" "$SCI_SHARD_SIZE" "$SCI_COMPRESSION" "$SCI_VERSION"
build_one_pack "en-history-250k" "history,biography,culture,politics" "$HIS_TARGET" "$HIS_SHARD_SIZE" "$HIS_COMPRESSION" "$HIS_VERSION"

echo "Focus packs generated and published under: $SITE_ROOT"
