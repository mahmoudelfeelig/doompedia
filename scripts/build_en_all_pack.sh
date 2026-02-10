#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PIPELINE_SRC="$ROOT_DIR/data-pipeline/src"
DUMPS_DIR="${DUMPS_DIR:-$ROOT_DIR/data/dumps/enwiki/latest}"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/data/out/en-all}"
TARGET="${TARGET:-999999999}"
SHARD_SIZE="${SHARD_SIZE:-40000}"
COMPRESSION="${COMPRESSION:-gzip}"
PACK_VERSION="${PACK_VERSION:-1}"
PACK_ID="${PACK_ID:-en-all-summaries}"

PAGE_SQL_GZ="$DUMPS_DIR/enwiki-latest-page.sql.gz"
PROPS_SQL_GZ="$DUMPS_DIR/enwiki-latest-page_props.sql.gz"
CARDS_NDJSON="$OUT_DIR/cards.ndjson"
PACK_DIR="$OUT_DIR/pack-v${PACK_VERSION}"

mkdir -p "$DUMPS_DIR" "$OUT_DIR" "$PACK_DIR"

if [[ ! -f "$PAGE_SQL_GZ" ]]; then
  curl -L --continue-at - -o "$PAGE_SQL_GZ" \
    https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-page.sql.gz
fi

if [[ ! -f "$PROPS_SQL_GZ" ]]; then
  curl -L --continue-at - -o "$PROPS_SQL_GZ" \
    https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-page_props.sql.gz
fi

PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.build_en_1m_from_sql \
  --page-sql-gz "$PAGE_SQL_GZ" \
  --page-props-sql-gz "$PROPS_SQL_GZ" \
  --output-ndjson "$CARDS_NDJSON" \
  --target "$TARGET" \
  --oversample 1.05

actual_count=$(wc -l < "$CARDS_NDJSON" | tr -d '[:space:]')

PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.build_pack \
  --input "$CARDS_NDJSON" \
  --output "$PACK_DIR" \
  --pack-id "$PACK_ID" \
  --language en \
  --max-records "$actual_count" \
  --shard-size "$SHARD_SIZE" \
  --version "$PACK_VERSION" \
  --compression "$COMPRESSION"

echo "Built full EN pack at: $PACK_DIR"
echo "Records: $actual_count"
