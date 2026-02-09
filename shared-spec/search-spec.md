# Search Behavior Contract (MVP)

## Scope
- Title-only search over local cards and alias table.
- No summary full-text index in MVP.

## Normalization
- Unicode normalization: NFKC.
- Case folding to lower case.
- Collapse repeated whitespace into a single space.

## Ranking order
1. exact title (`normalized_title == query`)
2. title prefix (`normalized_title LIKE query%`)
3. alias exact/prefix
4. typo candidates with edit distance <= 1

## Typo rules
- Enabled only for query length >= 5.
- Candidate set bounded to prevent latency spikes.
- Candidate prefilter by first character and length window.

## Multilingual
- Default search over current language pack.
- Optional cross-language toggle may query all installed packs.
