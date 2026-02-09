#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SITE_DIR="${SITE_DIR:-$ROOT_DIR/data/site}"
S3_BUCKET="${S3_BUCKET:-}"
S3_PREFIX="${S3_PREFIX:-}"

if [[ -z "$S3_BUCKET" ]]; then
  echo "S3_BUCKET is required, e.g. S3_BUCKET=my-doompedia-cdn"
  exit 1
fi

if [[ ! -d "$SITE_DIR" ]]; then
  echo "Site directory not found: $SITE_DIR"
  echo "Run scripts/publish_pack.sh first."
  exit 1
fi

TARGET="s3://$S3_BUCKET"
if [[ -n "$S3_PREFIX" ]]; then
  TARGET="$TARGET/$S3_PREFIX"
fi

aws s3 sync "$SITE_DIR" "$TARGET" --delete
echo "Deployed static pack site to $TARGET"
