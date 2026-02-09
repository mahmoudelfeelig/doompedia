# Product Decisions (Locked)

## 1) Dataset scope
- Initial release ships one language pack: `en`.
- Total cards target: `1,000,000`.
- Source namespace: main/article namespace only (`ns=0`).
- Redirect pages are not cards, but redirect titles are stored as aliases for search.
- Disambiguation pages are excluded from feed ranking but remain searchable.
- Summary length constraint: `40` to `320` UTF-8 characters.
- Required card fields:
  - `page_id`
  - `title`
  - `normalized_title`
  - `summary`
  - `wiki_url`
  - `topic_key`
  - `quality_score`
  - `is_disambiguation`
  - `source_rev_id`

## 2) Source, license, and attribution
- Bulk content is generated from Wikimedia dump data through pipeline jobs.
- App must provide a dedicated attribution screen with:
  - source statement,
  - CC BY-SA license notice,
  - license URL,
  - direct source link per card.
- App copy must clearly state non-affiliation with Wikimedia Foundation.
- App naming and branding should avoid trademark confusion.

## 3) Pack and update strategy
- Logical pack id: `en-core-1m`.
- Delivery model: chunked shards with a top-level manifest.
- Typical shard sizing target: `~40k` cards per shard.
- Download policy defaults:
  - Wi-Fi only: enabled
  - resume support: enabled
  - integrity check (sha256): required
- Delta patch format:
  - NDJSON operations (`upsert`, `delete`)
  - default transport uses uncompressed files for broad runtime compatibility
  - optional gzip compression supported by pipeline and Android runtime
  - iOS runtime currently targets uncompressed payloads in-app
  - operations keyed by `page_id`, versioned by `source_rev_id`
- Fallback:
  - if delta chain too long, checksum mismatch, or apply failure occurs, force full shard refresh.

## 4) Performance and storage targets
- Download size target: `<= 600 MB` for the initial pack.
- Installed data target: `<= 1.5 GB` on disk.
- Cold start p95: `<= 2.5s` to first usable feed.
- Feed rendering: `>= 55 FPS` on representative mid-tier devices.
- Search latency p95: `<= 150ms` for top-20 results at query length >= 3.
- Crash-free sessions target: `>= 99.5%`.

## 5) Search behavior (MVP)
- Search scope: title-centric only (no full summary FTS in MVP).
- Match/rank order:
  1. exact title
  2. title prefix
  3. alias exact/prefix
  4. typo-corrected candidates
- Typo policy:
  - edit distance <= 1
  - applied only for query length >= 5
  - bounded candidate set for deterministic performance
- Multilingual behavior:
  - default searches current language pack
  - optional "all installed languages" toggle

## 6) Personalization and adaptivity
- Default personalization level: `LOW`.
- Optional levels: `OFF`, `LOW`, `MEDIUM`.
- Deterministic scoring formula:
  - `score = interest + novelty + diversity + quality - repetitionPenalty`
- Guardrails:
  - exploration floor: 25%
  - max same-topic cards per 12-card window
  - minimum distinct topics in feed window
  - daily learning drift cap
- Explainability:
  - each recommendation exposes the main factors in a compact "Why this?" panel.

## 7) Offline/online UX constraints
- Fully offline after pack install for:
  - feed
  - search
  - bookmarks
  - history
  - settings
- Card tap behavior:
  - open universal URL
  - OS resolves to Wikipedia app if available
  - otherwise fallback to browser
- Offline tap fallback:
  - clear message that full article requires connection.

## 8) Accessibility acceptance criteria
- WCAG AA contrast compliance.
- Dynamic text scaling support (up to large accessibility sizes).
- Screen reader labels and focus order verified.
- Reduced motion respect.
- Touch targets:
  - iOS >= 44pt
  - Android >= 48dp

## 9) Architecture boundary
- Native app implementations remain separate (`android-app`, `ios-app`).
- Behavior contracts are centralized in `shared-spec`.
- Ranking weights and guardrails are loaded from shared JSON.
