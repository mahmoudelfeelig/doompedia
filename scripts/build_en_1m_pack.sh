#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PIPELINE_SRC="$ROOT_DIR/data-pipeline/src"
DUMPS_DIR="${DUMPS_DIR:-$ROOT_DIR/data/dumps/enwiki/latest}"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/data/out/en-1m}"
TARGET="${TARGET:-1000000}"
SHARD_SIZE="${SHARD_SIZE:-40000}"
COMPRESSION="${COMPRESSION:-none}"
PACK_VERSION="${PACK_VERSION:-1}"
PROGRESS_EVERY="${PROGRESS_EVERY:-50000}"
PACK_PROGRESS_EVERY="${PACK_PROGRESS_EVERY:-100000}"

PAGE_SQL_GZ="$DUMPS_DIR/enwiki-latest-page.sql.gz"
PROPS_SQL_GZ="$DUMPS_DIR/enwiki-latest-page_props.sql.gz"
CARDS_NDJSON="$OUT_DIR/cards.ndjson"
PACK_DIR="$OUT_DIR/pack-v${PACK_VERSION}"

log() {
  printf '[build_en_1m_pack] %s %s\n' "$(date +'%Y-%m-%d %H:%M:%S')" "$*"
}

mkdir -p "$DUMPS_DIR" "$OUT_DIR" "$PACK_DIR"

if [[ ! -f "$PAGE_SQL_GZ" ]]; then
  log "Downloading page dump: $PAGE_SQL_GZ"
  curl -L --continue-at - -o "$PAGE_SQL_GZ" \
    https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-page.sql.gz
fi

if [[ ! -f "$PROPS_SQL_GZ" ]]; then
  log "Downloading page_props dump: $PROPS_SQL_GZ"
  curl -L --continue-at - -o "$PROPS_SQL_GZ" \
    https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-page_props.sql.gz
fi

log "Extracting cards from dumps..."
PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.build_en_1m_from_sql \
  --page-sql-gz "$PAGE_SQL_GZ" \
  --page-props-sql-gz "$PROPS_SQL_GZ" \
  --output-ndjson "$CARDS_NDJSON" \
  --target "$TARGET" \
  --progress-every "$PROGRESS_EVERY"

log "Building sharded pack..."
PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.build_pack \
  --input "$CARDS_NDJSON" \
  --output "$PACK_DIR" \
  --pack-id en-core-1m \
  --language en \
  --max-records "$TARGET" \
  --shard-size "$SHARD_SIZE" \
  --version "$PACK_VERSION" \
  --compression "$COMPRESSION" \
  --progress-every "$PACK_PROGRESS_EVERY"

log "Built pack at: $PACK_DIR"
