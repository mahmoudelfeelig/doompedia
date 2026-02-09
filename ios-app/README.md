# Doompedia iOS

Native iOS client (Swift + SwiftUI) with local SQLite storage, deterministic ranking, and offline card browsing.

## Implemented in this scaffold
- SQLite schema for article cards, aliases, bookmarks, history, and topic affinity.
- Seed bootstrap from bundled JSON.
- Local pack install classes: `PackInstaller`, `DeltaApplier`, and resumable `ShardDownloader`.
- Manifest update service with manual checks and launch-time periodic auto checks.
- Deterministic ranking with shared `ranking-config.v1.json`.
- Title search: exact, prefix, alias, and typo distance <= 1.
- Personalization controls (`Off`, `Low`, `Medium`) and theme controls.
- Card action opens Wikipedia URL using universal links.

## Build options
- Use Xcode directly by creating an App target that includes `Doompedia/` sources and resources.
- Or use `xcodegen` with `project.yml`:
  ```bash
  cd ios-app
  xcodegen generate
  open Doompedia.xcodeproj
  ```

## Notes
- `seed_en_cards.json` is demonstration data. Production content should be imported from shard packs built in `data-pipeline`.
- Search is title-only by product decision to keep storage and latency bounded at 1M records.
- Set a manifest URL in Settings to enable remote pack updates.
- Runtime install/update supports `none` and `gzip` shard/delta payloads.
