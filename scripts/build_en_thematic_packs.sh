#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PIPELINE_SRC="$ROOT_DIR/data-pipeline/src"

SOURCE_MANIFEST="${SOURCE_MANIFEST:-$ROOT_DIR/data/site/packs/en-all-summaries/v1/manifest.json}"
if [[ ! -f "$SOURCE_MANIFEST" ]]; then
  SOURCE_MANIFEST="$ROOT_DIR/data/site/packs/en-core-1m/v1/manifest.json"
fi

OUT_ROOT="${OUT_ROOT:-$ROOT_DIR/data/out/thematic-packs}"
SITE_ROOT="${SITE_ROOT:-$ROOT_DIR/data/site/packs}"
BASE_URL="${BASE_URL:-https://packs.example.invalid/packs}"
LANGUAGE="${LANGUAGE:-en}"
COMPRESSION="${COMPRESSION:-gzip}"
VERSION="${VERSION:-1}"

if [[ ! -f "$SOURCE_MANIFEST" ]]; then
  echo "Source manifest not found. Build/publish en-all-summaries or en-core-1m first."
  echo "Tried: $SOURCE_MANIFEST"
  exit 1
fi

mkdir -p "$OUT_ROOT" "$SITE_ROOT"

build_one_pack() {
  local pack_id="$1"
  local topics="$2"
  local target="$3"
  local shard_size="$4"

  local pack_out="$OUT_ROOT/${pack_id}"
  local cards_path="$pack_out/cards.ndjson"
  local pack_dir="$pack_out/pack-v${VERSION}"
  local publish_dir="$SITE_ROOT/${pack_id}/v${VERSION}"
  local latest_pointer="$SITE_ROOT/${pack_id}/latest.json"
  local base_url="$BASE_URL/${pack_id}/v${VERSION}"

  mkdir -p "$pack_out" "$pack_dir" "$publish_dir"

  echo "Building subset cards for ${pack_id} (target=${target}, topics=${topics})..."
  PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.build_topic_subset \
    --source-manifest "$SOURCE_MANIFEST" \
    --output-ndjson "$cards_path" \
    --language "$LANGUAGE" \
    --allowed-topics "$topics" \
    --target "$target"

  local actual_count
  actual_count=$(wc -l < "$cards_path" | tr -d '[:space:]')
  if [[ "$actual_count" -eq 0 ]]; then
    echo "No cards generated for ${pack_id}. Check source/topic rules."
    exit 1
  fi

  echo "Sharding/building ${pack_id} (${actual_count} records)..."
  PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.build_pack \
    --input "$cards_path" \
    --output "$pack_dir" \
    --pack-id "$pack_id" \
    --language "$LANGUAGE" \
    --max-records "$actual_count" \
    --shard-size "$shard_size" \
    --version "$VERSION" \
    --compression "$COMPRESSION"

  echo "Publishing ${pack_id} to site folder..."
  PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.publish_pack \
    --pack-dir "$pack_dir" \
    --output-dir "$publish_dir" \
    --base-url "$base_url" \
    --latest-pointer "$latest_pointer" \
    --clean

  echo "Done: ${pack_id} -> ${publish_dir}"
}

# Defaults chosen for meaningful topical splits while keeping shard counts manageable.
STEM_TARGET="${STEM_TARGET:-500000}"
STEM_SHARD_SIZE="${STEM_SHARD_SIZE:-40000}"
HISTORY_TARGET="${HISTORY_TARGET:-500000}"
HISTORY_SHARD_SIZE="${HISTORY_SHARD_SIZE:-40000}"
BIO_TARGET="${BIO_TARGET:-500000}"
BIO_SHARD_SIZE="${BIO_SHARD_SIZE:-40000}"
GEO_TARGET="${GEO_TARGET:-500000}"
GEO_SHARD_SIZE="${GEO_SHARD_SIZE:-40000}"
CULTURE_TARGET="${CULTURE_TARGET:-500000}"
CULTURE_SHARD_SIZE="${CULTURE_SHARD_SIZE:-40000}"

build_one_pack "en-stem-500k" "science,technology,health,environment" "$STEM_TARGET" "$STEM_SHARD_SIZE"
build_one_pack "en-history-politics-500k" "history,politics,economics" "$HISTORY_TARGET" "$HISTORY_SHARD_SIZE"
build_one_pack "en-biography-500k" "biography" "$BIO_TARGET" "$BIO_SHARD_SIZE"
build_one_pack "en-geography-500k" "geography" "$GEO_TARGET" "$GEO_SHARD_SIZE"
build_one_pack "en-culture-500k" "culture,society,sports" "$CULTURE_TARGET" "$CULTURE_SHARD_SIZE"

echo "Thematic packs generated and published under: $SITE_ROOT"
echo "Upload to R2 with:"
echo "  R2_BUCKET=<bucket> R2_ACCOUNT_ID=<account_id> ./scripts/deploy_pack_to_r2.sh"
