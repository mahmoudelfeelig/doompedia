# Dataset Build Result (Local Run)

## Build command
```bash
./scripts/build_en_1m_pack.sh
```

## Output artifacts
- Cards NDJSON: `data/out/en-1m/cards.ndjson`
- Pack manifest: `data/out/en-1m/pack-v1/manifest.json`
- Shards directory: `data/out/en-1m/pack-v1/shards/`
- Checksums: `data/out/en-1m/pack-v1/checksums.txt`

## Verified result
- `cards.ndjson` rows: `1,000,000`
- Pack id: `en-core-1m`
- Version: `1`
- Shards: `25`
- Compression: `none`
- Published pack verification:
  - `manifest.recordCount = 1,000,000`
  - declared shard record sum = `1,000,000`
  - actual shard line sum = `1,000,000`
  - missing shards = `0`

## Verification command
```bash
python3 data-pipeline/src/doompedia_pipeline/verify_pack.py \
  --manifest data/site/packs/en-core-1m/v1/manifest.json \
  --count-lines
```

## Notes
- Raw dump inputs are stored locally under `data/dumps/enwiki/latest/`.
- Generated dump and pack artifacts are intentionally git-ignored via `.gitignore`:
  - `data/dumps/`
  - `data/out/`
