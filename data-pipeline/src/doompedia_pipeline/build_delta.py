from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from io import TextIOWrapper
from pathlib import Path

from .models import CardRecord
from .normalize import clamp_summary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build NDJSON delta between two card snapshots")
    parser.add_argument("--base", required=True, help="Base snapshot NDJSON")
    parser.add_argument("--target", required=True, help="Target snapshot NDJSON")
    parser.add_argument("--output", required=True, help="Output delta file")
    parser.add_argument(
        "--compression",
        choices=["none", "gzip"],
        default="none",
        help="Delta compression format",
    )
    return parser.parse_args()


def _load_snapshot(path: Path) -> dict[int, dict[str, object]]:
    rows: dict[int, dict[str, object]] = {}
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            payload = json.loads(line)
            summary = clamp_summary(str(payload.get("summary", "")))
            if summary is None:
                continue
            payload["summary"] = summary
            record = CardRecord.from_json(payload)
            rows[record.page_id] = {
                "record": record.as_article_payload(),
                "aliases": record.aliases,
            }
    return rows


def build_delta(
    base_path: Path,
    target_path: Path,
    output_path: Path,
    compression: str = "none",
) -> dict[str, int | str]:
    base = _load_snapshot(base_path)
    target = _load_snapshot(target_path)

    output_path.parent.mkdir(parents=True, exist_ok=True)

    upserts = 0
    deletes = 0

    if compression == "gzip":
        writer = gzip.open(output_path, "wb")
        stream: TextIOWrapper | None = TextIOWrapper(writer, encoding="utf-8")
    else:
        stream = output_path.open("w", encoding="utf-8")

    assert stream is not None
    with stream as handle:
        for page_id, target_payload in target.items():
            if page_id not in base or target_payload != base[page_id]:
                handle.write(json.dumps({"op": "upsert", **target_payload}, ensure_ascii=False))
                handle.write("\n")
                upserts += 1

        for page_id in sorted(set(base).difference(target)):
            handle.write(json.dumps({"op": "delete", "page_id": page_id}))
            handle.write("\n")
            deletes += 1

    hasher = hashlib.sha256()
    with output_path.open("rb") as fh:
        while True:
            block = fh.read(1024 * 1024)
            if not block:
                break
            hasher.update(block)

    return {
        "sha256": hasher.hexdigest(),
        "upserts": upserts,
        "deletes": deletes,
        "ops": upserts + deletes,
        "bytes": output_path.stat().st_size,
        "compression": compression,
    }


def main() -> None:
    args = parse_args()
    summary = build_delta(
        base_path=Path(args.base),
        target_path=Path(args.target),
        output_path=Path(args.output),
        compression=args.compression,
    )
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
