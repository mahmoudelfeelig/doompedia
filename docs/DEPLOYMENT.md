# Deployment Guide

## Goal
Host `manifest.json` and shard files on HTTPS so Android and iOS can fetch pack updates.

The manifest is the "table of contents" for your 1M pack. It tells the app:
- pack id/version
- which shard files exist
- checksum/hash for each shard
- optional delta patch info

## 1) Build packs locally

### Core 1M pack
```bash
./scripts/build_en_1m_pack.sh
```

### Topic-focused packs (real, non-stub)
This generates and publishes:
- `en-science-250k` (currently ~16.8k cards available from the source profile)
- `en-history-250k` (250k cards)
```bash
./scripts/build_en_focus_packs.sh
```

### Thematic split packs (real, individually downloadable)
Builds and publishes:
- `en-stem-500k`
- `en-history-politics-500k`
- `en-biography-500k`
- `en-geography-500k`
- `en-culture-500k`
```bash
./scripts/build_en_thematic_packs.sh
```

### Full EN "all available summaries" pack (rest of available articles)
This is the large job. It downloads Wikimedia SQL dumps and builds `en-all-summaries`.
```bash
./scripts/build_en_all_pack.sh
```

Expected output:
- `data/out/en-1m/pack-v1/manifest.json`
- `data/out/en-1m/pack-v1/shards/*`
- `data/out/focus-packs/en-science-250k/pack-v1/*`
- `data/out/focus-packs/en-history-250k/pack-v1/*`
- `data/out/thematic-packs/*/pack-v1/*`
- `data/out/en-all/pack-v1/*`

## 2) Prepare hosted site layout
### Publish core pack
```bash
BASE_URL="https://packs.example.com/packs/en-core-1m/v1" \
./scripts/publish_pack.sh
```

This writes:
- `data/site/packs/en-core-1m/v1/manifest.json`
- `data/site/packs/en-core-1m/v1/shards/*`
- `data/site/packs/en-core-1m/latest.json`

### Publish full EN-all pack
```bash
./scripts/publish_en_all_pack.sh
```

## 3) Verify local hosted pack completeness
```bash
python3 data-pipeline/src/doompedia_pipeline/verify_pack.py \
  --manifest data/site/packs/en-core-1m/v1/manifest.json \
  --count-lines
```

Expected key values:
- `"status": "ok"`
- `"recordCount": 1000000`
- `"actualShardLineSum": 1000000`

You can repeat verify for additional packs:
```bash
python3 data-pipeline/src/doompedia_pipeline/verify_pack.py \
  --manifest data/site/packs/en-history-250k/v1/manifest.json \
  --count-lines
```

## 4) Upload to Cloudflare R2 (S3-compatible)
Set R2 credentials once (PowerShell or shell environment):
```bash
export AWS_ACCESS_KEY_ID="<R2_ACCESS_KEY_ID>"
export AWS_SECRET_ACCESS_KEY="<R2_SECRET_ACCESS_KEY>"
```

Then upload everything under `data/site` to your R2 bucket:
```bash
R2_BUCKET=doompedia-packs \
R2_ACCOUNT_ID=<your_cloudflare_account_id> \
R2_PREFIX="" \
./scripts/deploy_pack_to_r2.sh
```

### Exact command sequence for "download rest articles + upload"
Run this from repo root:
```bash
# 1) Build the very large EN-all pack (downloads remaining dump inputs as needed)
./scripts/build_en_all_pack.sh

# 2) Publish EN-all manifest/shards into data/site
./scripts/publish_en_all_pack.sh

# 3) Upload all hosted packs (core + focus + en-all) to Cloudflare R2
R2_BUCKET=doompedia-packs \
R2_ACCOUNT_ID=<your_cloudflare_account_id> \
R2_PREFIX="" \
./scripts/deploy_pack_to_r2.sh
```

Equivalent raw AWS CLI form (same result):
```bash
aws s3 sync data/site s3://doompedia-packs \
  --delete \
  --region auto \
  --endpoint-url https://<your_cloudflare_account_id>.r2.cloudflarestorage.com
```

PowerShell equivalent:
```powershell
$env:AWS_ACCESS_KEY_ID = "<R2_ACCESS_KEY_ID>"
$env:AWS_SECRET_ACCESS_KEY = "<R2_SECRET_ACCESS_KEY>"
aws s3 sync .\data\site s3://doompedia-packs --delete --region auto --endpoint-url https://<your_cloudflare_account_id>.r2.cloudflarestorage.com
```

Then map your domain (for example `packs.example.invalid`) to the R2 public/custom domain endpoint in Cloudflare and ensure HTTPS is enabled.

## 5) Configure Cloudflare caching
Recommended Cache Rules:
- Path contains `/packs/` -> Cache: `Eligible for cache`
- Edge TTL:
  - shard files (`*.ndjson`, `*.ndjson.gz`): long TTL (7-30 days)
  - manifest (`manifest.json`, `latest.json`): short TTL (60-300 seconds)

Recommended response headers:
- shard files:
  - `Cache-Control: public, max-age=31536000, immutable`
- manifest files:
  - `Cache-Control: public, max-age=120, must-revalidate`

## 6) Guardrails so you stay inside free limits
- Keep app updates manual-only (already default in this project).
- Keep `Wi-Fi only` enabled by default.
- Prefer `gzip` shards to reduce egress.
- In Cloudflare, enable usage notifications/alerts for R2 and bandwidth.
- Start with one pack (`en-core-1m/v1`) and avoid frequent full-pack re-publishes.

## 6.1) Data-only updates without full rebuild
If app code/UI changed only:
- no pack rebuild
- no republish to R2

If only one dataset changed (for example adding image metadata to one pack):
1. Rebuild only that pack.
2. Republish only that pack into `data/site/packs/<pack-id>/v<version>`.
3. Run `deploy_pack_to_r2.sh` (it uses `aws s3 sync`, so only changed files upload).

Optional smaller on-device updates (delta patch instead of full re-download):
```bash
BASE_CARDS=./data/out/en-all/cards.previous.ndjson \
TARGET_CARDS=./data/out/en-all/cards.ndjson \
PACK_DIR=./data/out/en-all/pack-v2 \
BASE_VERSION=1 \
TARGET_VERSION=2 \
./scripts/build_pack_delta.sh
```

Then publish normally:
```bash
PACK_DIR=./data/out/en-all/pack-v2 \
OUTPUT_DIR=./data/site/packs/en-all-summaries/v2 \
BASE_URL=https://packs.example.invalid/packs/en-all-summaries/v2 \
LATEST_POINTER=./data/site/packs/en-all-summaries/latest.json \
./scripts/publish_en_all_pack.sh
```

## 7) Configure app manifest URL
In app Packs tab, set one of:
- `https://packs.example.invalid/packs/en-core-1m/v1/manifest.json`
- `https://packs.example.invalid/packs/en-history-250k/v1/manifest.json`
- `https://packs.example.invalid/packs/en-science-250k/v1/manifest.json`
- `https://packs.example.invalid/packs/en-all-summaries/v1/manifest.json` (after building/publishing EN-all)

Use HTTPS only; Android blocks cleartext HTTP by default.

## 8) Verify update path on device
- Open app Packs tab.
- Enter manifest URL.
- Tap `Check updates now`.
- Confirm installed pack version updates.
- Turn airplane mode on and verify feed/search still work offline.
