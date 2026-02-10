# Doompedia Data Pipeline

Builds chunked offline packs and delta updates for summary-card datasets.

## Input record contract
Input files are newline-delimited JSON with these fields:
- `page_id` (int)
- `lang` (str)
- `title` (str)
- `summary` (str)
- `wiki_url` (str)
- `topic_key` (str)
- `entity_type` (str, optional; e.g. `person`, `place`, `event`, `concept`)
- `keywords` (list[str], optional)
- `quality_score` (float, optional)
- `is_disambiguation` (bool/int, optional)
- `source_rev_id` (int, optional)
- `updated_at` (ISO-8601 string, optional)
- `aliases` (list[str], optional)

## Build pack
```bash
cd data-pipeline
python -m doompedia_pipeline.build_pack \
  --input /path/to/en_cards.ndjson \
  --output /path/to/out \
  --pack-id en-core-1m \
  --language en \
  --max-records 1000000 \
  --shard-size 40000 \
  --compression none
```

Outputs:
- `manifest.json`
- `shards/shard-0001.ndjson` (or `.ndjson.gz` when `--compression gzip`) ...
- `checksums.txt`

## Extract cards from Wikimedia XML dump
```bash
python -m doompedia_pipeline.extract_dump \
  --input /path/to/enwiki-pages-articles.xml \
  --output /path/to/cards.ndjson \
  --language en \
  --max-records 1000000
```

## Build 1M real cards from Wikimedia SQL dumps
This path is optimized for title + short-description cards (no full-article parsing).

1) Download required dumps:
```bash
mkdir -p dumps/enwiki/latest
curl -L --continue-at - -o dumps/enwiki/latest/enwiki-latest-page.sql.gz \
  https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-page.sql.gz
curl -L --continue-at - -o dumps/enwiki/latest/enwiki-latest-page_props.sql.gz \
  https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-page_props.sql.gz
```

2) Build NDJSON cards:
```bash
python -m doompedia_pipeline.build_en_1m_from_sql \
  --page-sql-gz dumps/enwiki/latest/enwiki-latest-page.sql.gz \
  --page-props-sql-gz dumps/enwiki/latest/enwiki-latest-page_props.sql.gz \
  --output-ndjson out/en-1m/cards.ndjson \
  --target 1000000
```

For a full "all available EN summaries" build, use the helper script:
```bash
./scripts/build_en_all_pack.sh
```
This uses a very high target and emits an `en-all-summaries` pack based on all matching records in the SQL dumps.

3) Build installable shard pack:
```bash
python -m doompedia_pipeline.build_pack \
  --input out/en-1m/cards.ndjson \
  --output out/en-1m/pack-v1 \
  --pack-id en-core-1m \
  --language en \
  --max-records 1000000 \
  --shard-size 40000 \
  --compression none
```

4) Publish pack for static hosting (optional):
```bash
python -m doompedia_pipeline.publish_pack \
  --pack-dir out/en-1m/pack-v1 \
  --output-dir out/site/packs/en-core-1m/v1 \
  --base-url https://example.org/packs/en-core-1m/v1 \
  --latest-pointer out/site/packs/en-core-1m/latest.json \
  --clean
```

Equivalent convenience wrapper:
```bash
BASE_URL=https://example.org/packs/en-core-1m/v1 \
./scripts/publish_pack.sh
```

## Build delta
```bash
python -m doompedia_pipeline.build_delta \
  --base /path/to/base_cards.ndjson \
  --target /path/to/target_cards.ndjson \
  --output /path/to/out/delta-v2.ndjson \
  --compression none
```

The delta format is NDJSON operations:
- `{"op": "upsert", "record": {...}}`
- `{"op": "delete", "page_id": 123}`

## Notes
- Normalization and summary filtering align with `shared-spec` decisions.
- Default compression is `none` for broad mobile runtime compatibility.
- Use `--compression gzip` when distribution infrastructure supports it.
- Delta generation is deterministic when `updated_at` is stable per record.
