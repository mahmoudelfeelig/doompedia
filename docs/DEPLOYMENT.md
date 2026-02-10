# Deployment Guide

## Goal
Host `manifest.json` and shard files on HTTPS so Android and iOS can fetch pack updates.

The manifest is the "table of contents" for your 1M pack. It tells the app:
- pack id/version
- which shard files exist
- checksum/hash for each shard
- optional delta patch info

## 1) Build the pack locally
```bash
./scripts/build_en_1m_pack.sh
```

For a full EN "all available summaries" build:
```bash
./scripts/build_en_all_pack.sh
```

Expected output:
- `data/out/en-1m/pack-v1/manifest.json`
- `data/out/en-1m/pack-v1/shards/*`

## 2) Prepare hosted site layout
```bash
BASE_URL="https://packs.example.com/packs/en-core-1m/v1" \
./scripts/publish_pack.sh
```

This writes:
- `data/site/packs/en-core-1m/v1/manifest.json`
- `data/site/packs/en-core-1m/v1/shards/*`
- `data/site/packs/en-core-1m/latest.json`

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

## 4) Upload to Cloudflare R2 (S3-compatible)
If using AWS CLI:
```bash
S3_BUCKET=my-doompedia-cdn \
S3_PREFIX=doompedia \
./scripts/deploy_pack_to_s3.sh
```

Then map your domain (for example `packs.example.invalid`) to the R2 public/custom domain endpoint in Cloudflare.

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

## 7) Configure app manifest URL
In app Packs tab set:
`https://packs.example.com/packs/en-core-1m/v1/manifest.json`

Use HTTPS only; Android blocks cleartext HTTP by default.

## 8) Verify update path on device
- Open app Packs tab.
- Enter manifest URL.
- Tap `Check updates now`.
- Confirm installed pack version updates.
- Turn airplane mode on and verify feed/search still work offline.
