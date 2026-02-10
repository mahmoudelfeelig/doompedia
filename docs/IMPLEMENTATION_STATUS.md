# Implementation Status

## Done
- Shared contracts are in place (`shared-spec/`) for schema, ranking config, search behavior, and pack manifest.
- Data pipeline supports dump extraction, 1M build, shard pack generation, delta generation, publish layout, and pack verification.
- `data/site/packs/en-core-1m/v1` contains a complete 1,000,000-card pack:
  - 25 shards present
  - manifest declares 1,000,000 records
  - verifier confirms actual shard line total is 1,000,000
- Android app implements:
  - feed/search/offline bootstrap
  - card tap opens article URL
  - recommendation controls (`Show more`, `Show less`)
  - info button (`i`) for descriptive "Why this is shown"
  - save folders (multi-folder assignment) with Bookmarks default
  - Saved tab (folder create/delete/select)
  - settings import/export JSON
  - personalization levels `OFF/LOW/MEDIUM/HIGH`
  - theme mode + accent color presets + custom hex accent
  - manual updates only
- iOS app implements parity for the above core features (feed/search/saved/settings/adaptive controls/manual updates).
- iOS CI workflow exists to build from Windows via GitHub-hosted macOS:
  - `.github/workflows/ios-build.yml`

## Remaining external work (not code gaps)
- End-to-end validation on physical Android and iOS devices across a real test matrix.
- App-store signing/provisioning and release publishing configuration.
