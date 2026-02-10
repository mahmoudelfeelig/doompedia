# Platform Parity Matrix

| Capability | Android (Kotlin) | iOS (Swift) |
|---|---|---|
| Offline card feed | Implemented | Implemented |
| Title-only search | Implemented | Implemented |
| Alias search | Implemented | Implemented |
| Typo distance <= 1 | Implemented | Implemented |
| Deterministic adaptive ranking | Implemented | Implemented |
| Personalization controls (`OFF/LOW/MEDIUM/HIGH`) | Implemented | Implemented |
| Light/Dark/System mode + custom accent color | Implemented | Implemented |
| Attribution in Settings | Implemented | Implemented |
| Bookmarks | Implemented | Implemented |
| Saved folders (multi-group assignment) | Implemented | Implemented |
| Saved tab folder CRUD | Implemented | Implemented |
| "Show more / Show less" recommendation controls | Implemented | Implemented |
| Settings import/export | Implemented | Implemented |
| Local history/topic affinity | Implemented | Implemented |
| Wikipedia app/browser deep-link fallback | Implemented | Implemented |
| Chunked shard importer from network | Implemented | Implemented |
| Delta patch application | Implemented | Implemented |
| Manual "check updates now" flow | Implemented | Implemented |
| Periodic auto update checks | Disabled (manual-only) | Disabled (manual-only) |

## Notes
- Both platforms share ranking and manifest contracts from `shared-spec/`.
- Both platforms bootstrap from bundled seed data and can upgrade using remote pack manifests.
- Manifest-driven update path supports delta-first with full-pack fallback.
- Both platforms include topic normalization heuristics so older/general topic labels are corrected at read/import time.
- Compression support:
  - Android: `none` and `gzip` shard/delta payloads.
  - iOS: `none` and `gzip` shard/delta payloads.
