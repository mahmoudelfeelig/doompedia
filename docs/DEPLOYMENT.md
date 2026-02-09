# Deployment Guide

## Goal
Host `manifest.json` and shard files on a static endpoint that both apps can download from.

## 1) Build the pack locally
```bash
./scripts/build_en_1m_pack.sh
```

Expected output:
- `data/out/en-1m/pack-v1/manifest.json`
- `data/out/en-1m/pack-v1/shards/*`

## 2) Prepare hosted site layout
```bash
BASE_URL="https://cdn.example.org/packs/en-core-1m/v1" \
./scripts/publish_pack.sh
```

This writes:
- `data/site/packs/en-core-1m/v1/manifest.json`
- `data/site/packs/en-core-1m/v1/shards/*`
- `data/site/packs/en-core-1m/latest.json`

## 3) Upload to S3-compatible storage
```bash
S3_BUCKET=my-doompedia-cdn \
S3_PREFIX=doompedia \
./scripts/deploy_pack_to_s3.sh
```

## Optional: CI release artifact builds
- Manual workflow: `.github/workflows/release.yml`
- Produces:
  - Android release build artifacts (`apk`/`aab`)
  - iOS simulator release zip

## 4) Configure app manifest URL
In app settings, set manifest URL to the hosted manifest path, for example:
`https://cdn.example.org/packs/en-core-1m/v1/manifest.json`

## 5) Verify update path
- Tap `Check updates now` in Android/iOS settings.
- Confirm installed pack version increases.
- Confirm search/feed still function offline after update.
