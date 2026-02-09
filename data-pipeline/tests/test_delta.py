import gzip
import json
from pathlib import Path

from doompedia_pipeline.build_delta import build_delta


def _write(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row))
            handle.write("\n")


def test_build_delta(tmp_path: Path) -> None:
    base = tmp_path / "base.ndjson"
    target = tmp_path / "target.ndjson"
    output = tmp_path / "delta.ndjson.gz"

    base_rows = [
        {
            "page_id": 1,
            "lang": "en",
            "title": "Ada Lovelace",
            "summary": "Ada Lovelace was an English mathematician and writer known for her work on Charles Babbage's early mechanical computer.",
            "wiki_url": "https://en.wikipedia.org/wiki/Ada_Lovelace",
            "topic_key": "science"
        },
        {
            "page_id": 99,
            "lang": "en",
            "title": "To Be Deleted",
            "summary": "This article is present in base and removed from target for delete-op coverage.",
            "wiki_url": "https://en.wikipedia.org/wiki/To_Be_Deleted",
            "topic_key": "general"
        }
    ]

    target_rows = [
        {
            "page_id": 1,
            "lang": "en",
            "title": "Ada Lovelace",
            "summary": "Ada Lovelace was an English mathematician and writer best known for her contributions to early computing.",
            "wiki_url": "https://en.wikipedia.org/wiki/Ada_Lovelace",
            "topic_key": "science"
        },
        {
            "page_id": 2,
            "lang": "en",
            "title": "Grace Hopper",
            "summary": "Grace Hopper was an American computer scientist, mathematician, and United States Navy rear admiral.",
            "wiki_url": "https://en.wikipedia.org/wiki/Grace_Hopper",
            "topic_key": "science"
        }
    ]

    _write(base, base_rows)
    _write(target, target_rows)

    result = build_delta(base, target, output, compression="gzip")
    assert result["ops"] == 3

    with gzip.open(output, "rt", encoding="utf-8") as handle:
        lines = [json.loads(line) for line in handle if line.strip()]

    assert len(lines) == 3
    assert {line["op"] for line in lines} == {"upsert", "delete"}


def test_build_delta_same_snapshot_is_empty(tmp_path: Path) -> None:
    snapshot = tmp_path / "snapshot.ndjson"
    output = tmp_path / "delta.ndjson"

    rows = [
        {
            "page_id": 7,
            "lang": "en",
            "title": "Same Snapshot",
            "summary": "This row is identical in base and target so the delta should be empty.",
            "wiki_url": "https://en.wikipedia.org/wiki/Same_Snapshot",
            "topic_key": "general",
        }
    ]

    _write(snapshot, rows)
    result = build_delta(snapshot, snapshot, output, compression="none")
    assert result["ops"] == 0
    assert output.read_text(encoding="utf-8") == ""
