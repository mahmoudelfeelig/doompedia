# Platform Parity Matrix

| Capability | Android (Kotlin) | iOS (Swift) |
|---|---|---|
| Offline card feed | Implemented | Implemented |
| Title-only search | Implemented | Implemented |
| Alias search | Implemented | Implemented |
| Typo distance <= 1 | Implemented | Implemented |
| Deterministic adaptive ranking | Implemented | Implemented |
| Personalization controls | Implemented | Implemented |
| Light/Dark/System mode | Implemented | Implemented |
| Attribution screen | Implemented | Implemented |
| Bookmarks | Implemented | Implemented |
| Local history/topic affinity | Implemented | Implemented |
| Wikipedia app/browser deep-link fallback | Implemented | Implemented |
| Chunked shard importer from network | Implemented | Implemented |
| Delta patch application | Implemented | Implemented |
| Manual "check updates now" flow | Implemented | Implemented |
| Periodic auto update checks | Implemented via WorkManager | Implemented on launch cadence |

## Notes
- Both platforms share ranking and manifest contracts from `shared-spec/`.
- Both platforms bootstrap from bundled seed data and can upgrade using remote pack manifests.
- Manifest-driven update path supports delta-first with full-pack fallback.
- Compression support:
  - Android: `none` and `gzip` shard/delta payloads.
  - iOS: `none` payloads in runtime; gzip decoding is intentionally deferred.
