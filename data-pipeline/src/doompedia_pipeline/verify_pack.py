from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify Doompedia pack completeness and counts")
    parser.add_argument("--manifest", required=True, help="Path to manifest.json")
    parser.add_argument(
        "--count-lines",
        action="store_true",
        help="Count actual NDJSON lines in every shard (slower but stronger verification)",
    )
    return parser.parse_args()


def _open_text(path: Path):
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open("r", encoding="utf-8")


def verify_pack(manifest_path: Path, count_lines: bool) -> dict[str, object]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    pack_dir = manifest_path.parent
    shards = manifest.get("shards", [])
    expected_record_count = int(manifest.get("recordCount", 0))

    missing: list[str] = []
    declared_sum = 0
    actual_sum = 0

    for shard in shards:
        shard_url = str(shard.get("url", ""))
        shard_name = shard_url.split("/")[-1]
        shard_path = pack_dir / "shards" / shard_name
        if not shard_path.exists():
            missing.append(shard_name)
            continue

        declared_records = int(shard.get("records", 0))
        declared_sum += declared_records

        if count_lines:
            with _open_text(shard_path) as fh:
                actual_sum += sum(1 for _ in fh)

    status = "ok"
    messages: list[str] = []
    if missing:
        status = "failed"
        messages.append(f"Missing shards: {len(missing)}")
    if declared_sum != expected_record_count:
        status = "failed"
        messages.append(
            f"Manifest recordCount mismatch: recordCount={expected_record_count}, declaredShardSum={declared_sum}"
        )
    if count_lines and actual_sum != expected_record_count:
        status = "failed"
        messages.append(
            f"Actual line count mismatch: recordCount={expected_record_count}, actualLineSum={actual_sum}"
        )

    return {
        "status": status,
        "packId": manifest.get("packId"),
        "version": manifest.get("version"),
        "recordCount": expected_record_count,
        "shardCount": len(shards),
        "declaredShardRecordSum": declared_sum,
        "actualShardLineSum": actual_sum if count_lines else None,
        "missingShards": missing,
        "messages": messages,
    }


def main() -> None:
    args = parse_args()
    result = verify_pack(Path(args.manifest), count_lines=args.count_lines)
    print(json.dumps(result, indent=2))
    if result["status"] != "ok":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
