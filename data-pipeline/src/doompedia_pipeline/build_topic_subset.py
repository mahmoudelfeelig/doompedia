from __future__ import annotations

import argparse
import gzip
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

from .normalize import clamp_summary

_WORD_RE = re.compile(r"[A-Za-z][A-Za-z-]{2,}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create a topic-focused NDJSON card subset from an existing pack manifest."
    )
    parser.add_argument("--source-manifest", required=True, help="Path to source manifest.json")
    parser.add_argument("--output-ndjson", required=True, help="Output NDJSON path")
    parser.add_argument("--language", default="en")
    parser.add_argument("--allowed-topics", required=True, help="Comma-separated topics")
    parser.add_argument("--target", type=int, default=250_000)
    return parser.parse_args()


def _normalize_token(value: str) -> str:
    text = unicodedata.normalize("NFKC", value).casefold().strip()
    text = text.replace("_", "-").replace(" ", "-")
    text = re.sub(r"-+", "-", text)
    return text


def _canonical_topic(raw_topic: str) -> str:
    normalized = _normalize_token(raw_topic)
    if normalized == "history-of":
        return "history"
    if normalized == "geography-of":
        return "geography"
    if normalized == "economy-of":
        return "economics"
    if normalized == "list-of":
        return "culture"
    return normalized


def _infer_topic(title: str, summary: str, raw_topic: str) -> str:
    canonical = _canonical_topic(raw_topic)
    stable = {
        "science",
        "technology",
        "history",
        "geography",
        "culture",
        "politics",
        "economics",
        "sports",
        "health",
        "environment",
        "society",
        "biography",
    }
    if canonical in stable:
        return canonical

    text = f"{title} {summary}".lower()
    rules: list[tuple[str, tuple[str, ...]]] = [
        ("biography", ("born", "died", "actor", "author", "scientist", "politician", "player")),
        ("history", ("empire", "war", "century", "kingdom", "revolution", "historical")),
        ("science", ("physics", "chemistry", "biology", "mathematics", "astronomy", "scientific")),
        ("technology", ("software", "computer", "internet", "digital", "algorithm", "device")),
        ("geography", ("river", "mountain", "city", "country", "region", "province", "capital")),
        ("politics", ("election", "government", "parliament", "minister", "policy", "party")),
        ("economics", ("economy", "market", "trade", "finance", "currency", "industry")),
        ("health", ("disease", "medical", "medicine", "health", "hospital", "symptom")),
        ("sports", ("football", "basketball", "olympic", "league", "athlete", "championship")),
        ("environment", ("climate", "ecology", "forest", "wildlife", "pollution", "conservation")),
        ("culture", ("music", "film", "literature", "art", "religion", "language")),
    ]
    for topic, keywords in rules:
        if any(keyword in text for keyword in keywords):
            return topic
    return "general"


def _infer_entity_type(title: str, summary: str, topic_key: str) -> str:
    text = f"{title} {summary}".lower()
    if topic_key == "biography" or any(token in text for token in (" born ", " died ", " actor ", " author ")):
        return "person"
    if topic_key == "geography" or any(token in text for token in (" city ", " country ", " river ", " mountain ")):
        return "place"
    if topic_key == "history" or any(token in text for token in (" war ", " battle ", " revolution ")):
        return "event"
    return "concept"


def _infer_keywords(title: str, summary: str, topic_key: str) -> list[str]:
    seen: set[str] = set()
    keywords: list[str] = []
    stopwords = {
        "about", "after", "before", "their", "there", "which", "while", "where", "these", "those",
        "through", "using", "under", "between", "during", "known", "wikipedia", "article",
    }

    def add(raw: str) -> None:
        normalized = _normalize_token(raw)
        if not normalized or normalized in seen:
            return
        seen.add(normalized)
        keywords.append(normalized)

    add(topic_key)
    for token in _WORD_RE.findall(title.lower()):
        if len(token) >= 4 and token not in stopwords:
            add(token)
        if len(keywords) >= 8:
            return keywords[:12]

    for token in _WORD_RE.findall(summary.lower()):
        if len(token) >= 5 and token not in stopwords:
            add(token)
        if len(keywords) >= 12:
            return keywords[:12]

    return keywords[:12]


def _resolve_shard_path(source_manifest: Path, shard_url: str) -> Path:
    base_dir = source_manifest.parent
    direct = base_dir / shard_url
    if direct.exists():
        return direct
    fallback = base_dir / "shards" / Path(shard_url).name
    if fallback.exists():
        return fallback
    raise FileNotFoundError(f"Shard not found for URL {shard_url!r}")


def _read_shard_lines(shard_path: Path):
    if shard_path.suffix == ".gz":
        with gzip.open(shard_path, "rt", encoding="utf-8") as handle:
            for line in handle:
                yield line
        return
    with shard_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            yield line


def build_topic_subset(
    source_manifest: Path,
    output_ndjson: Path,
    language: str,
    allowed_topics: set[str],
    target: int,
) -> dict[str, object]:
    manifest = json.loads(source_manifest.read_text(encoding="utf-8"))
    output_ndjson.parent.mkdir(parents=True, exist_ok=True)

    written = 0
    scanned = 0
    topic_counts: dict[str, int] = defaultdict(int)

    with output_ndjson.open("w", encoding="utf-8") as out:
        for shard in manifest.get("shards", []):
            shard_path = _resolve_shard_path(source_manifest=source_manifest, shard_url=shard["url"])
            for line in _read_shard_lines(shard_path):
                line = line.strip()
                if not line:
                    continue
                scanned += 1

                payload = json.loads(line)
                article = payload.get("article") or {}
                if article.get("lang") != language:
                    continue

                title = str(article.get("title", "")).strip()
                summary_raw = str(article.get("summary", "")).strip()
                summary = clamp_summary(summary_raw)
                if not title or summary is None:
                    continue

                topic_key = _infer_topic(
                    title=title,
                    summary=summary,
                    raw_topic=str(article.get("topic_key", "general")),
                )
                if topic_key not in allowed_topics:
                    continue

                raw_disambiguation = article.get("is_disambiguation", False)
                if isinstance(raw_disambiguation, bool):
                    is_disambiguation = raw_disambiguation
                elif isinstance(raw_disambiguation, int):
                    is_disambiguation = raw_disambiguation != 0
                elif isinstance(raw_disambiguation, str):
                    is_disambiguation = raw_disambiguation.strip().lower() in {"1", "true", "yes", "y"}
                else:
                    is_disambiguation = False

                record = {
                    "page_id": int(article["page_id"]),
                    "lang": language,
                    "title": title,
                    "summary": summary,
                    "wiki_url": str(article.get("wiki_url", "")),
                    "topic_key": topic_key,
                    "quality_score": float(article.get("quality_score", 0.5)),
                    "is_disambiguation": bool(is_disambiguation),
                    "source_rev_id": article.get("source_rev_id"),
                    "updated_at": str(article.get("updated_at", "1970-01-01T00:00:00Z")),
                    "entity_type": _infer_entity_type(title=title, summary=summary, topic_key=topic_key),
                    "keywords": _infer_keywords(title=title, summary=summary, topic_key=topic_key),
                    "aliases": payload.get("aliases", []) or [],
                }
                out.write(json.dumps(record, ensure_ascii=False))
                out.write("\n")

                written += 1
                topic_counts[topic_key] += 1
                if written >= target:
                    return {
                        "written": written,
                        "target": target,
                        "scanned": scanned,
                        "topics": dict(sorted(topic_counts.items())),
                    }

    return {
        "written": written,
        "target": target,
        "scanned": scanned,
        "topics": dict(sorted(topic_counts.items())),
    }


def main() -> None:
    args = parse_args()
    allowed_topics = {
        _normalize_token(item)
        for item in args.allowed_topics.split(",")
        if item.strip()
    }
    if not allowed_topics:
        raise SystemExit("--allowed-topics must include at least one topic")

    result = build_topic_subset(
        source_manifest=Path(args.source_manifest),
        output_ndjson=Path(args.output_ndjson),
        language=args.language,
        allowed_topics=allowed_topics,
        target=args.target,
    )
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
