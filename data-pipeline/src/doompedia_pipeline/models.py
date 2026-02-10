from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from .normalize import normalize_title


@dataclass(slots=True)
class CardRecord:
    page_id: int
    lang: str
    title: str
    summary: str
    wiki_url: str
    topic_key: str
    quality_score: float = 0.5
    is_disambiguation: bool = False
    source_rev_id: int | None = None
    updated_at: str = "1970-01-01T00:00:00Z"
    entity_type: str = "concept"
    keywords: list[str] = field(default_factory=list)
    aliases: list[str] = field(default_factory=list)

    @property
    def normalized_title(self) -> str:
        return normalize_title(self.title)

    def as_article_payload(self) -> dict[str, Any]:
        return {
            "page_id": self.page_id,
            "lang": self.lang,
            "title": self.title,
            "normalized_title": self.normalized_title,
            "summary": self.summary,
            "wiki_url": self.wiki_url,
            "topic_key": self.topic_key,
            "quality_score": self.quality_score,
            "is_disambiguation": bool(self.is_disambiguation),
            "source_rev_id": self.source_rev_id,
            "updated_at": self.updated_at,
            "entity_type": self.entity_type,
            "keywords": self.keywords,
        }

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "CardRecord":
        raw_is_disambiguation = payload.get("is_disambiguation", False)
        if isinstance(raw_is_disambiguation, bool):
            is_disambiguation = raw_is_disambiguation
        elif isinstance(raw_is_disambiguation, int):
            is_disambiguation = raw_is_disambiguation != 0
        elif isinstance(raw_is_disambiguation, str):
            is_disambiguation = raw_is_disambiguation.strip().lower() in {
                "1", "true", "yes", "y", "t",
            }
        else:
            is_disambiguation = False

        return cls(
            page_id=int(payload["page_id"]),
            lang=str(payload["lang"]),
            title=str(payload["title"]),
            summary=str(payload["summary"]),
            wiki_url=str(payload["wiki_url"]),
            topic_key=str(payload.get("topic_key", "general")),
            quality_score=float(payload.get("quality_score", 0.5)),
            is_disambiguation=is_disambiguation,
            source_rev_id=(
                int(payload["source_rev_id"])
                if payload.get("source_rev_id") is not None
                else None
            ),
            updated_at=str(payload.get("updated_at", "1970-01-01T00:00:00Z")),
            entity_type=str(payload.get("entity_type", "concept")),
            keywords=[str(keyword) for keyword in payload.get("keywords", [])][:12],
            aliases=[str(alias) for alias in payload.get("aliases", [])],
        )
