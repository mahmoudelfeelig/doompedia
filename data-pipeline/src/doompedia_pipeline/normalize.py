from __future__ import annotations

import re
import unicodedata

_WHITESPACE_RE = re.compile(r"\s+")


def normalize_title(value: str) -> str:
    """Normalize titles and aliases for deterministic title search."""
    text = unicodedata.normalize("NFKC", value).casefold().strip()
    return _WHITESPACE_RE.sub(" ", text)


def clamp_summary(summary: str, minimum: int = 40, maximum: int = 320) -> str | None:
    """Apply the product summary-length constraints.

    Returns `None` if summary is too short.
    """
    cleaned = _WHITESPACE_RE.sub(" ", summary.strip())
    if len(cleaned) < minimum:
        return None
    if len(cleaned) > maximum:
        return cleaned[: maximum - 1].rstrip() + "…"
    return cleaned
