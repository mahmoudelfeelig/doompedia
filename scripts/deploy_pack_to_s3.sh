#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
SITE_DIR="${SITE_DIR:-$ROOT_DIR/data/site}"
S3_BUCKET="${S3_BUCKET:-}"
S3_PREFIX="${S3_PREFIX:-}"
S3_ENDPOINT_URL="${S3_ENDPOINT_URL:-}"
AWS_REGION="${AWS_REGION:-auto}"
AWS_PROFILE="${AWS_PROFILE:-}"
AWS_CLI="${AWS_CLI:-}"

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

resolve_aws_cli() {
  if [[ -n "$AWS_CLI" ]]; then
    if command -v "$AWS_CLI" >/dev/null 2>&1 || [[ -x "$AWS_CLI" ]]; then
      AWS_CLI_CMD=("$AWS_CLI")
      return 0
    fi
    echo "Configured AWS_CLI is not executable: $AWS_CLI"
    exit 1
  fi

  if command -v aws >/dev/null 2>&1; then
    AWS_CLI_CMD=("aws")
    return 0
  fi

  if command -v aws.exe >/dev/null 2>&1; then
    AWS_CLI_CMD=("aws.exe")
    return 0
  fi

  if [[ -x "/mnt/c/Program Files/Amazon/AWSCLIV2/aws.exe" ]]; then
    AWS_CLI_CMD=("/mnt/c/Program Files/Amazon/AWSCLIV2/aws.exe")
    return 0
  fi

  if [[ -x "/c/Program Files/Amazon/AWSCLIV2/aws.exe" ]]; then
    AWS_CLI_CMD=("/c/Program Files/Amazon/AWSCLIV2/aws.exe")
    return 0
  fi

  echo "AWS CLI not found."
  echo "Install AWS CLI in this shell, or set AWS_CLI to the executable path."
  echo "Example: AWS_CLI='/mnt/c/Program Files/Amazon/AWSCLIV2/aws.exe'"
  exit 1
}

resolve_aws_cli

normalize_site_dir_for_cli() {
  local raw="$1"

  # If we ended up using the Windows AWS CLI executable from WSL/Git Bash,
  # convert Unix-like paths so aws.exe can resolve the local source directory.
  if [[ "${AWS_CLI_CMD[0],,}" == *.exe ]]; then
    if command -v wslpath >/dev/null 2>&1; then
      wslpath -w "$raw"
      return 0
    fi
    if command -v cygpath >/dev/null 2>&1; then
      cygpath -w "$raw"
      return 0
    fi
  fi

  echo "$raw"
}

SITE_DIR_FOR_CLI="$(normalize_site_dir_for_cli "$SITE_DIR")"

sync_cmd=("${AWS_CLI_CMD[@]}" s3 sync "$SITE_DIR_FOR_CLI" "$TARGET" --delete --region "$AWS_REGION" --no-progress)
if [[ -n "$S3_ENDPOINT_URL" ]]; then
  sync_cmd+=(--endpoint-url "$S3_ENDPOINT_URL")
fi
if [[ -n "$AWS_PROFILE" ]]; then
  sync_cmd+=(--profile "$AWS_PROFILE")
fi

"${sync_cmd[@]}"
echo "Deployed static pack site to $TARGET"
