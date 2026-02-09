# Implementation Guide

## Cross-platform parity checklist
- Shared content schema (`shared-spec/content-schema.sql`) is implemented in both apps.
- Shared ranking config (`shared-spec/ranking-config.v1.json`) is parsed by both apps.
- Shared search rules are implemented identically for:
  - title normalization,
  - exact/prefix ordering,
  - typo edit distance threshold.
- Card open behavior uses same URL construction and fallback semantics.

## Data flow
1. Pipeline creates shard files + manifest.
2. App downloads shard files under app-managed storage.
3. Importer validates checksums and applies upserts transactionally.
4. User interactions update local topic affinity.
5. Feed ranking combines card metadata, affinity, novelty, diversity and guardrails.
6. Update flow attempts delta first, then falls back to full shard refresh.

## Failure handling
- Shard checksum mismatch:
  - mark shard invalid,
  - retry with backoff,
  - never partially apply.
- Delta apply failure:
  - stop chain,
  - request full shard refresh.
- Compression policy:
  - default `none` compression for broad compatibility,
  - optional `gzip` path supported by pipeline and Android installer,
  - iOS installer currently accepts `none` payloads only.
- DB migration failure:
  - preserve old DB,
  - prompt user to retry migration/update.

## Security and privacy
- No user account identifiers.
- No cloud sync payload in MVP.
- Optional diagnostics must be explicit opt-in and aggregate-oriented.
- User activity used for ranking remains on device.

## Test priorities
- Ranking determinism from fixed fixtures.
- Search ordering and typo tolerance edge cases.
- Import idempotency and rollback on corruption.
- Offline behavior for all critical screens.
