from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from io import TextIOWrapper
from pathlib import Path

from .models import CardRecord
from .normalize import clamp_summary, normalize_title


@dataclass(slots=True)
class ShardMeta:
    id: str
    path: str
    sha256: str
    records: int
    bytes: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build shard-based Doompedia pack")
    parser.add_argument("--input", required=True, help="NDJSON input path")
    parser.add_argument("--output", required=True, help="Output directory")
    parser.add_argument("--pack-id", required=True)
    parser.add_argument("--language", default="en")
    parser.add_argument("--max-records", type=int, default=1_000_000)
    parser.add_argument("--shard-size", type=int, default=40_000)
    parser.add_argument("--version", type=int, default=1)
    parser.add_argument(
        "--compression",
        choices=["none", "gzip"],
        default="none",
        help="Shard compression format",
    )
    return parser.parse_args()


def iter_cards(path: Path, language: str):
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            payload = json.loads(line)
            if payload.get("lang", language) != language:
                continue
            summary = clamp_summary(str(payload.get("summary", "")))
            if summary is None:
                continue
            payload["summary"] = summary
            payload["lang"] = language
            record = CardRecord.from_json(payload)
            record.title = record.title.strip()
            if not record.title:
                continue
            yield record


def write_shard(
    shards_dir: Path,
    shard_index: int,
    records: list[CardRecord],
    compression: str,
) -> ShardMeta:
    extension = ".ndjson.gz" if compression == "gzip" else ".ndjson"
    shard_name = f"shard-{shard_index:04d}{extension}"
    shard_path = shards_dir / shard_name
    hasher = hashlib.sha256()

    if compression == "gzip":
        writer = gzip.open(shard_path, "wb")
        stream: TextIOWrapper | None = TextIOWrapper(writer, encoding="utf-8")
    else:
        stream = shard_path.open("w", encoding="utf-8")

    assert stream is not None
    with stream as out:
        for record in records:
            article = record.as_article_payload()
            article["normalized_title"] = normalize_title(article["title"])
            out.write(json.dumps({"article": article, "aliases": record.aliases}, ensure_ascii=False))
            out.write("\n")

    with shard_path.open("rb") as fh:
        while True:
            block = fh.read(1024 * 1024)
            if not block:
                break
            hasher.update(block)

    shard_id = shard_name
    if shard_id.endswith(".ndjson.gz"):
        shard_id = shard_id.removesuffix(".ndjson.gz")
    else:
        shard_id = shard_id.removesuffix(".ndjson")

    return ShardMeta(
        id=shard_id,
        path=f"shards/{shard_name}",
        sha256=hasher.hexdigest(),
        records=len(records),
        bytes=shard_path.stat().st_size,
    )


def build_pack(args: argparse.Namespace) -> dict[str, object]:
    input_path = Path(args.input)
    output_dir = Path(args.output)
    shards_dir = output_dir / "shards"
    output_dir.mkdir(parents=True, exist_ok=True)
    shards_dir.mkdir(parents=True, exist_ok=True)

    shard_buffer: list[CardRecord] = []
    shard_metas: list[ShardMeta] = []
    topic_counts = defaultdict(int)
    entity_counts = defaultdict(int)
    keyword_counts = defaultdict(int)
    processed = 0

    for card in iter_cards(input_path, args.language):
        if processed >= args.max_records:
            break
        processed += 1
        topic_counts[card.topic_key] += 1
        entity_counts[card.entity_type] += 1
        for keyword in card.keywords[:8]:
            keyword_counts[keyword] += 1
        shard_buffer.append(card)

        if len(shard_buffer) >= args.shard_size:
            shard_metas.append(
                write_shard(
                    shards_dir=shards_dir,
                    shard_index=len(shard_metas) + 1,
                    records=shard_buffer,
                    compression=args.compression,
                )
            )
            shard_buffer = []

    if shard_buffer:
        shard_metas.append(
            write_shard(
                shards_dir=shards_dir,
                shard_index=len(shard_metas) + 1,
                records=shard_buffer,
                compression=args.compression,
            )
        )

    top_topics = sorted(topic_counts.items(), key=lambda item: item[1], reverse=True)
    top_keywords = sorted(keyword_counts.items(), key=lambda item: item[1], reverse=True)

    manifest = {
        "packId": args.pack_id,
        "language": args.language,
        "version": args.version,
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "recordCount": processed,
        "compression": args.compression,
        "description": f"Doompedia {args.language.upper()} pack with {processed:,} summary cards.",
        "packTags": [topic for topic, _ in top_topics[:12]],
        "shards": [
            {
                "id": meta.id,
                "url": meta.path,
                "sha256": meta.sha256,
                "records": meta.records,
                "bytes": meta.bytes,
            }
            for meta in shard_metas
        ],
        "attribution": {
            "source": "Wikipedia content snapshots",
            "license": "CC BY-SA 4.0",
            "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
            "requiredNotice": "This app uses Wikipedia content. Wikipedia is a trademark of the Wikimedia Foundation."
        },
        "topicDistribution": dict(sorted(topic_counts.items())),
        "entityDistribution": dict(sorted(entity_counts.items())),
        "sampleKeywords": [keyword for keyword, _ in top_keywords[:40]],
    }

    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    checksum_lines = [f"{meta.sha256}  {meta.path}" for meta in shard_metas]
    (output_dir / "checksums.txt").write_text("\n".join(checksum_lines) + "\n", encoding="utf-8")

    return manifest


def main() -> None:
    args = parse_args()
    manifest = build_pack(args)
    print(json.dumps({
        "packId": manifest["packId"],
        "version": manifest["version"],
        "recordCount": manifest["recordCount"],
        "shards": len(manifest["shards"]),
    }, indent=2))


if __name__ == "__main__":
    main()
