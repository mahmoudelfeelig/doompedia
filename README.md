# Doompedia

Offline-first discovery app with 1M Wikipedia summary cards, native Android and iOS clients, and a shared data/ranking specification.

## Product summary
- Cards are available offline after initial setup.
- Card tap opens the full article via Wikipedia universal link (Wikipedia app if installed, otherwise browser).
- No account system and no cloud sync.
- Deterministic adaptive ranking with explicit controls and transparent explanations.
- Manifest-driven pack updates with delta-first fallback to full shard refresh.
- Dedicated attribution/legal surface aligned to Wikimedia requirements.

## Repository layout
- `docs/` product and technical decisions.
- `shared-spec/` shared schema + ranking + manifest contracts.
- `data-pipeline/` pack build and delta tooling.
- `android-app/` Kotlin + Jetpack Compose implementation.
- `ios-app/` Swift + SwiftUI implementation.

## Locked defaults
- `1,000,000` cards from English Wikipedia main namespace (`ns=0`).
- Disambiguation pages excluded from feed but retained for search routing.
- Title-only search in MVP (exact/prefix/alias + small typo tolerance).
- Personalization default: `LOW` with `OFF` and `MEDIUM` options.
- Chunked pack install with resumable shard download and checksum validation.
- Default transport compression is `none`, with optional `gzip` pipeline output (supported by both Android and iOS runtimes).

## Offline behavior
- Works offline for feed, bookmarks, history, settings, and title search.
- Opening full article requires connection unless external app/browser already has cached content.

## Build note
This implementation is intentionally structured for thesis-grade reproducibility and clarity:
- deterministic ranking config,
- shared manifest/schema contracts,
- explicit acceptance budgets in docs.

## Build real 1M data pack
Use `scripts/build_en_1m_pack.sh` to generate a real `en-core-1m` pack from Wikimedia dumps:
- downloads `enwiki-latest-page.sql.gz` and `enwiki-latest-page_props.sql.gz`,
- builds 1,000,000 title+description+URL card records,
- emits shard pack + manifest under `data/out/en-1m/pack-v1/`.

## Publish/deploy pack
- Prepare hosted pack layout: `scripts/publish_pack.sh`
- Deploy to S3-compatible storage: `scripts/deploy_pack_to_s3.sh`
- See full guide: `docs/DEPLOYMENT.md`

## Release readiness
- Full release checklist: `docs/RELEASE_CHECKLIST.md`
- Local data build result snapshot: `docs/DATASET_BUILD_RESULT.md`
