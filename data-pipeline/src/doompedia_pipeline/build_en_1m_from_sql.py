from __future__ import annotations

import argparse
import gzip
import json
import re
import unicodedata
from pathlib import Path
from urllib.parse import quote

from .normalize import clamp_summary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build 1M Doompedia cards from Wikimedia SQL dumps (page + page_props)"
    )
    parser.add_argument("--page-sql-gz", required=True, help="Path to enwiki-latest-page.sql.gz")
    parser.add_argument(
        "--page-props-sql-gz",
        required=True,
        help="Path to enwiki-latest-page_props.sql.gz",
    )
    parser.add_argument("--output-ndjson", required=True, help="Output NDJSON path")
    parser.add_argument("--language", default="en")
    parser.add_argument("--target", type=int, default=1_000_000)
    parser.add_argument("--oversample", type=float, default=2.2)
    return parser.parse_args()


INSERT_RE = re.compile(r"^INSERT INTO `(?P<table>[^`]+)` VALUES (?P<values>.+);$")
WORD_RE = re.compile(r"[A-Za-z][A-Za-z-]{2,}")


def _normalize_title(title: str) -> str:
    text = unicodedata.normalize("NFKC", title).casefold().strip()
    return re.sub(r"\s+", " ", text)


def _topic_key_from_text(title: str, shortdesc: str) -> str:
    base = title.replace("_", " ").strip().lower()
    if base.startswith("history of "):
        return "history"
    if base.startswith("geography of "):
        return "geography"
    if base.startswith("economy of "):
        return "economics"
    if base.startswith("list of "):
        return "culture"

    text = f"{base} {shortdesc.lower()}"
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


def _entity_type_from_text(title: str, summary: str, topic_key: str) -> str:
    text = f"{title} {summary}".lower()
    if topic_key == "biography" or any(token in text for token in (" born ", " died ", " actor ", " author ")):
        return "person"
    if topic_key == "geography" or any(token in text for token in (" city ", " country ", " river ", " mountain ")):
        return "place"
    if topic_key == "history" or any(token in text for token in (" war ", " battle ", " revolution ")):
        return "event"
    return "concept"


def _keywords_from_text(title: str, summary: str, topic_key: str) -> list[str]:
    seen: set[str] = set()
    keywords: list[str] = []
    stopwords = {
        "about", "after", "before", "their", "there", "which", "while", "where", "these", "those",
        "through", "using", "under", "between", "during", "known", "wikipedia", "article",
    }

    def add(raw: str) -> None:
        normalized = _normalize_title(raw).replace(" ", "-")
        if not normalized or normalized in seen:
            return
        seen.add(normalized)
        keywords.append(normalized)

    add(topic_key)

    for token in WORD_RE.findall(title.lower()):
        if len(token) >= 4 and token not in stopwords:
            add(token)
        if len(keywords) >= 8:
            break

    for token in WORD_RE.findall(summary.lower()):
        if len(token) >= 5 and token not in stopwords:
            add(token)
        if len(keywords) >= 12:
            break

    return keywords[:12]


def _parse_sql_values(values_blob: str) -> list[list[str | None]]:
    rows: list[list[str | None]] = []
    row: list[str | None] = []
    token_chars: list[str] = []

    in_quote = False
    escape = False
    in_row = False

    i = 0
    while i < len(values_blob):
        ch = values_blob[i]

        if in_quote:
            if escape:
                token_chars.append(ch)
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == "'":
                in_quote = False
            else:
                token_chars.append(ch)
            i += 1
            continue

        if ch == "'":
            in_quote = True
            i += 1
            continue

        if ch == "(":
            in_row = True
            row = []
            token_chars = []
            i += 1
            continue

        if ch == "," and in_row:
            token = "".join(token_chars).strip()
            row.append(None if token == "NULL" else token)
            token_chars = []
            i += 1
            continue

        if ch == ")" and in_row:
            token = "".join(token_chars).strip()
            row.append(None if token == "NULL" else token)
            rows.append(row)
            row = []
            token_chars = []
            in_row = False
            i += 1
            continue

        if in_row:
            token_chars.append(ch)
        i += 1

    return rows


def collect_candidate_pages(
    page_sql_gz: Path,
    target: int,
    oversample: float,
) -> list[tuple[int, str]]:
    desired = max(target, int(target * oversample))
    candidates: list[tuple[int, str]] = []

    with gzip.open(page_sql_gz, "rt", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.rstrip("\n")
            match = INSERT_RE.match(line)
            if not match or match.group("table") != "page":
                continue

            for row in _parse_sql_values(match.group("values")):
                # page table columns:
                # 0 page_id, 1 page_namespace, 2 page_title, 3 restrictions, 4 is_redirect, ...
                if len(row) < 5:
                    continue
                page_id_raw, ns_raw, title_raw, _, redirect_raw = row[:5]
                if page_id_raw is None or ns_raw is None or title_raw is None or redirect_raw is None:
                    continue
                if ns_raw != "0":
                    continue
                if redirect_raw != "0":
                    continue

                try:
                    page_id = int(page_id_raw)
                except ValueError:
                    continue

                title = title_raw
                candidates.append((page_id, title))
                if len(candidates) >= desired:
                    return candidates

    return candidates


def collect_shortdescs(
    page_props_sql_gz: Path,
    candidate_ids: set[int],
) -> dict[int, str]:
    shortdesc_by_id: dict[int, str] = {}
    if not candidate_ids:
        return shortdesc_by_id

    with gzip.open(page_props_sql_gz, "rt", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.rstrip("\n")
            match = INSERT_RE.match(line)
            if not match or match.group("table") != "page_props":
                continue

            for row in _parse_sql_values(match.group("values")):
                # page_props columns:
                # 0 pp_page, 1 pp_propname, 2 pp_value, 3 pp_sortkey
                if len(row) < 3:
                    continue
                page_id_raw, propname, value = row[:3]
                if page_id_raw is None or propname is None or value is None:
                    continue
                if propname != "wikibase-shortdesc":
                    continue
                try:
                    page_id = int(page_id_raw)
                except ValueError:
                    continue
                if page_id not in candidate_ids:
                    continue
                if page_id not in shortdesc_by_id:
                    shortdesc_by_id[page_id] = value

    return shortdesc_by_id


def _stabilize_summary(title: str, shortdesc: str) -> str | None:
    summary = shortdesc.strip()
    if not summary:
        return None

    clamped = clamp_summary(summary)
    if clamped is not None:
        return clamped

    # Preserve 40-char minimum while keeping real short description content.
    fallback = f"{title.replace('_', ' ')}: {summary}"
    if len(fallback) < 40:
        fallback = f"{fallback}. Wikipedia article."
    if len(fallback) < 40:
        fallback = f"{fallback} Reference entry."
    return clamp_summary(fallback)


def build_cards(
    page_sql_gz: Path,
    page_props_sql_gz: Path,
    output_ndjson: Path,
    language: str,
    target: int,
    oversample: float,
) -> dict[str, int]:
    candidates = collect_candidate_pages(page_sql_gz=page_sql_gz, target=target, oversample=oversample)
    candidate_ids = {page_id for page_id, _ in candidates}
    shortdescs = collect_shortdescs(page_props_sql_gz=page_props_sql_gz, candidate_ids=candidate_ids)

    output_ndjson.parent.mkdir(parents=True, exist_ok=True)
    written = 0

    with output_ndjson.open("w", encoding="utf-8") as out:
        for page_id, title in candidates:
            shortdesc = shortdescs.get(page_id)
            if not shortdesc:
                continue

            summary = _stabilize_summary(title=title, shortdesc=shortdesc)
            if summary is None:
                continue

            topic_key = _topic_key_from_text(title=title, shortdesc=summary)
            record = {
                "page_id": page_id,
                "lang": language,
                "title": title.replace("_", " "),
                "normalized_title": _normalize_title(title.replace("_", " ")),
                "summary": summary,
                "wiki_url": f"https://{language}.wikipedia.org/wiki/{quote(title)}",
                "topic_key": topic_key,
                "quality_score": 0.5,
                "is_disambiguation": "(disambiguation)" in title.lower(),
                "source_rev_id": None,
                "updated_at": "1970-01-01T00:00:00Z",
                "entity_type": _entity_type_from_text(title=title, summary=summary, topic_key=topic_key),
                "keywords": _keywords_from_text(title=title, summary=summary, topic_key=topic_key),
                "aliases": [],
            }
            out.write(json.dumps(record, ensure_ascii=False))
            out.write("\n")
            written += 1
            if written >= target:
                break

    return {
        "candidatePages": len(candidates),
        "shortdescsMatched": len(shortdescs),
        "written": written,
        "target": target,
    }


def main() -> None:
    args = parse_args()
    summary = build_cards(
        page_sql_gz=Path(args.page_sql_gz),
        page_props_sql_gz=Path(args.page_props_sql_gz),
        output_ndjson=Path(args.output_ndjson),
        language=args.language,
        target=args.target,
        oversample=args.oversample,
    )
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
