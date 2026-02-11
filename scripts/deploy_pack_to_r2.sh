#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

SITE_DIR="${SITE_DIR:-$ROOT_DIR/data/site}"
R2_BUCKET="${R2_BUCKET:-}"
R2_PREFIX="${R2_PREFIX:-}"
R2_ACCOUNT_ID="${R2_ACCOUNT_ID:-}"
R2_ENDPOINT_URL="${R2_ENDPOINT_URL:-}"
AWS_REGION="${AWS_REGION:-auto}"
AWS_PROFILE="${AWS_PROFILE:-}"

if [[ -z "$R2_BUCKET" ]]; then
  echo "R2_BUCKET is required, e.g. R2_BUCKET=doompedia-packs"
  exit 1
fi

if [[ -z "$R2_ENDPOINT_URL" ]]; then
  if [[ -z "$R2_ACCOUNT_ID" ]]; then
    echo "Set either R2_ENDPOINT_URL or R2_ACCOUNT_ID."
    echo "Example: R2_ACCOUNT_ID=abcd1234efgh5678"
    exit 1
  fi
  R2_ENDPOINT_URL="https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
fi

S3_BUCKET="$R2_BUCKET" \
S3_PREFIX="$R2_PREFIX" \
S3_ENDPOINT_URL="$R2_ENDPOINT_URL" \
AWS_REGION="$AWS_REGION" \
AWS_PROFILE="$AWS_PROFILE" \
SITE_DIR="$SITE_DIR" \
"$ROOT_DIR/scripts/deploy_pack_to_s3.sh"
