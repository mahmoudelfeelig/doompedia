# Implementation Status

## Implemented now
- Product decisions locked and documented.
- Shared spec files for schema, search, ranking config, and manifest contract.
- Data pipeline scripts for Wikimedia dump extraction, pack shard generation, and delta generation.
- Android native app scaffold with Room, ranking, search, settings, and article deep-link behavior.
- iOS native app scaffold with SQLite, ranking, search, settings, and article deep-link behavior.
- Local pack installer/delta applier/downloader on both platforms.
- Android periodic background update worker (manifest check + delta/full fallback).
- iOS manual update flow plus automatic periodic check on app launch.
- Repository CI workflow for pipeline tests plus Android unit-test and iOS build validation.
- Manual-dispatch release workflow for Android and iOS build artifacts.
- Dedicated attribution screen on both platforms.
- Offline link guard that informs user full article needs connection.
- Cross-platform ranking/search parity hardening (deterministic ordering + stronger guardrails).
- iOS runtime gzip shard/delta decoding support.
- Pack publishing/deployment tooling for static hosting.

## Remaining non-code release work
- Device-matrix performance validation against targets in product decisions.
- Store signing/provisioning setup for production app-store submission.
