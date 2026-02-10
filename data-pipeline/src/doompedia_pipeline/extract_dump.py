from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import quote

from .normalize import clamp_summary, normalize_title

_COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)
_REF_RE = re.compile(r"<ref[^>/]*?>.*?</ref>|<ref[^>]*/>", re.IGNORECASE | re.DOTALL)
_TAG_RE = re.compile(r"<[^>]+>")
_HEADING_RE = re.compile(r"^=+.*?=+$", re.MULTILINE)
_TEMPLATE_RE = re.compile(r"\{\{[^{}]*\}\}")
_CATEGORY_RE = re.compile(r"\[\[Category:([^|\]]+)", re.IGNORECASE)
_WIKILINK_RE = re.compile(r"\[\[([^|\]]+)(?:\|([^\]]+))?\]\]")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Extract Doompedia card NDJSON from Wikipedia XML dump")
    parser.add_argument("--input", required=True, help="Wikipedia pages-articles XML dump path")
    parser.add_argument("--output", required=True, help="Output NDJSON path")
    parser.add_argument("--language", default="en")
    parser.add_argument("--max-records", type=int, default=1_000_000)
    parser.add_argument("--min-summary", type=int, default=40)
    parser.add_argument("--max-summary", type=int, default=320)
    return parser.parse_args()


def extract_topic_key(title: str, wikitext: str, summary: str) -> str:
    match = _CATEGORY_RE.search(wikitext)
    if match:
        category = normalize_title(match.group(1))
        if "history" in category:
            return "history"
        if any(token in category for token in ("country", "cities", "mountains", "rivers", "geography")):
            return "geography"
        if any(token in category for token in ("science", "biology", "physics", "chemistry", "mathematics")):
            return "science"
        if any(token in category for token in ("technology", "computing", "software", "internet")):
            return "technology"
        if any(token in category for token in ("politics", "governments", "elections")):
            return "politics"
        if any(token in category for token in ("economy", "finance", "business", "companies")):
            return "economics"
        if any(token in category for token in ("sport", "athletes", "teams")):
            return "sports"
        if any(token in category for token in ("medicine", "health", "diseases")):
            return "health"
        if any(token in category for token in ("environment", "ecology", "climate")):
            return "environment"
        if any(token in category for token in ("artists", "music", "films", "literature", "culture", "religion")):
            return "culture"

    text = f"{title} {summary} {wikitext[:2000]}".lower()
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


def extract_summary(wikitext: str, min_summary: int, max_summary: int) -> str | None:
    text = wikitext
    text = _COMMENT_RE.sub(" ", text)
    text = _REF_RE.sub(" ", text)
    text = _HEADING_RE.sub(" ", text)

    # Remove simple template blocks iteratively for typical infobox/metadata removal.
    while True:
        updated = _TEMPLATE_RE.sub(" ", text)
        if updated == text:
            break
        text = updated

    text = _WIKILINK_RE.sub(lambda m: m.group(2) or m.group(1), text)
    text = _TAG_RE.sub(" ", text)
    text = text.replace("'''", " ").replace("''", " ")
    text = text.replace("&nbsp;", " ")

    paragraphs = [part.strip() for part in text.split("\n\n") if part.strip()]
    lead = paragraphs[0] if paragraphs else text.strip()
    if not lead:
        return None
    return clamp_summary(lead, minimum=min_summary, maximum=max_summary)


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _child_text(element: ET.Element, name: str) -> str | None:
    for child in element:
        if _local_name(child.tag) == name:
            return child.text
    return None


def _page_id(page: ET.Element) -> int | None:
    for child in page:
        if _local_name(child.tag) == "id":
            if child.text is None:
                return None
            return int(child.text)
    return None


def _revision(page: ET.Element) -> ET.Element | None:
    for child in page:
        if _local_name(child.tag) == "revision":
            return child
    return None


def _is_redirect(page: ET.Element) -> bool:
    for child in page:
        if _local_name(child.tag) == "redirect":
            return True
    return False


def extract_dump(
    input_path: Path,
    output_path: Path,
    language: str,
    max_records: int,
    min_summary: int,
    max_summary: int,
) -> dict[str, int]:
    output_path.parent.mkdir(parents=True, exist_ok=True)

    written = 0
    scanned = 0
    with output_path.open("w", encoding="utf-8") as out:
        context = ET.iterparse(input_path, events=("end",))
        for _, elem in context:
            if _local_name(elem.tag) != "page":
                continue

            scanned += 1
            try:
                ns_value = int(_child_text(elem, "ns") or "-1")
            except ValueError:
                ns_value = -1

            if ns_value != 0 or _is_redirect(elem):
                elem.clear()
                continue

            title = (_child_text(elem, "title") or "").strip()
            page_id = _page_id(elem)
            revision = _revision(elem)
            if not title or page_id is None or revision is None:
                elem.clear()
                continue

            text = _child_text(revision, "text") or ""
            summary = extract_summary(text, min_summary=min_summary, max_summary=max_summary)
            if summary is None:
                elem.clear()
                continue

            rev_id_text = _child_text(revision, "id")
            rev_id = int(rev_id_text) if rev_id_text and rev_id_text.isdigit() else None
            updated_at = _child_text(revision, "timestamp") or "1970-01-01T00:00:00Z"
            disambiguation = "{{disambiguation" in text.lower() or title.lower().endswith("(disambiguation)")
            article_url = f"https://{language}.wikipedia.org/wiki/{quote(title.replace(' ', '_'))}"

            payload = {
                "page_id": page_id,
                "lang": language,
                "title": title,
                "normalized_title": normalize_title(title),
                "summary": summary,
                "wiki_url": article_url,
                "topic_key": extract_topic_key(title=title, wikitext=text, summary=summary),
                "quality_score": 0.5,
                "is_disambiguation": bool(disambiguation),
                "source_rev_id": rev_id,
                "updated_at": updated_at,
                "aliases": [],
            }
            out.write(json.dumps(payload, ensure_ascii=False))
            out.write("\n")
            written += 1

            elem.clear()
            if written >= max_records:
                break

    return {
        "scanned": scanned,
        "written": written,
    }


def main() -> None:
    args = parse_args()
    summary = extract_dump(
        input_path=Path(args.input),
        output_path=Path(args.output),
        language=args.language,
        max_records=args.max_records,
        min_summary=args.min_summary,
        max_summary=args.max_summary,
    )
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
