#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PIPELINE_SRC="$ROOT_DIR/data-pipeline/src"

BASE_CARDS="${BASE_CARDS:-}"
TARGET_CARDS="${TARGET_CARDS:-}"
PACK_DIR="${PACK_DIR:-}"
BASE_VERSION="${BASE_VERSION:-}"
TARGET_VERSION="${TARGET_VERSION:-}"
DELTA_COMPRESSION="${DELTA_COMPRESSION:-gzip}"
DELTA_NAME="${DELTA_NAME:-}"

if [[ -z "$BASE_CARDS" || -z "$TARGET_CARDS" || -z "$PACK_DIR" || -z "$BASE_VERSION" ]]; then
  cat <<'EOF'
Usage:
  BASE_CARDS=<old cards.ndjson> \
  TARGET_CARDS=<new cards.ndjson> \
  PACK_DIR=<path to pack-vN dir> \
  BASE_VERSION=<installed version number> \
  [TARGET_VERSION=<target version number>] \
  [DELTA_COMPRESSION=gzip|none] \
  [DELTA_NAME=<custom file name>] \
  ./scripts/build_pack_delta.sh
EOF
  exit 1
fi

MANIFEST_PATH="$PACK_DIR/manifest.json"
if [[ ! -f "$MANIFEST_PATH" ]]; then
  echo "Manifest not found: $MANIFEST_PATH"
  exit 1
fi

if [[ -z "$TARGET_VERSION" ]]; then
  TARGET_VERSION="$(python3 - "$MANIFEST_PATH" <<'PY'
import json
import sys
with open(sys.argv[1], "r", encoding="utf-8") as f:
    manifest = json.load(f)
print(int(manifest.get("version", 0)))
PY
)"
fi

if [[ "$TARGET_VERSION" -le "$BASE_VERSION" ]]; then
  echo "TARGET_VERSION ($TARGET_VERSION) must be greater than BASE_VERSION ($BASE_VERSION)"
  exit 1
fi

if [[ -z "$DELTA_NAME" ]]; then
  if [[ "$DELTA_COMPRESSION" == "gzip" ]]; then
    DELTA_NAME="delta-v${BASE_VERSION}-to-v${TARGET_VERSION}.ndjson.gz"
  else
    DELTA_NAME="delta-v${BASE_VERSION}-to-v${TARGET_VERSION}.ndjson"
  fi
fi

DELTA_PATH="$PACK_DIR/$DELTA_NAME"
SUMMARY_PATH="$PACK_DIR/.delta-summary-v${BASE_VERSION}-to-v${TARGET_VERSION}.json"

echo "Building delta: $BASE_CARDS -> $TARGET_CARDS"
PYTHONPATH="$PIPELINE_SRC" python3 -m doompedia_pipeline.build_delta \
  --base "$BASE_CARDS" \
  --target "$TARGET_CARDS" \
  --output "$DELTA_PATH" \
  --compression "$DELTA_COMPRESSION" > "$SUMMARY_PATH"

DELTA_SHA256="$(python3 - "$SUMMARY_PATH" <<'PY'
import json
import sys
with open(sys.argv[1], "r", encoding="utf-8") as f:
    summary = json.load(f)
print(summary["sha256"])
PY
)"

DELTA_OPS="$(python3 - "$SUMMARY_PATH" <<'PY'
import json
import sys
with open(sys.argv[1], "r", encoding="utf-8") as f:
    summary = json.load(f)
print(int(summary["ops"]))
PY
)"

python3 - "$MANIFEST_PATH" "$BASE_VERSION" "$TARGET_VERSION" "$DELTA_NAME" "$DELTA_SHA256" "$DELTA_OPS" <<'PY'
import json
import sys

manifest_path = sys.argv[1]
base_version = int(sys.argv[2])
target_version = int(sys.argv[3])
delta_name = sys.argv[4]
delta_sha = sys.argv[5]
delta_ops = int(sys.argv[6])

with open(manifest_path, "r", encoding="utf-8") as f:
    manifest = json.load(f)

manifest["delta"] = {
    "baseVersion": base_version,
    "targetVersion": target_version,
    "url": delta_name,
    "sha256": delta_sha,
    "ops": delta_ops,
}

with open(manifest_path, "w", encoding="utf-8") as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY

echo "Delta written: $DELTA_PATH"
echo "Manifest updated: $MANIFEST_PATH"
echo "Ops: $DELTA_OPS"
