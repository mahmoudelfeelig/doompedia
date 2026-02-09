import json
from pathlib import Path

from doompedia_pipeline.publish_pack import publish_pack


def test_publish_pack_rewrites_urls_and_writes_latest(tmp_path: Path) -> None:
    pack_dir = tmp_path / "pack"
    (pack_dir / "shards").mkdir(parents=True)
    shard_path = pack_dir / "shards" / "shard-0001.ndjson"
    shard_path.write_text('{"article": {"page_id": 1}}\n', encoding="utf-8")

    manifest = {
        "packId": "en-core-1m",
        "language": "en",
        "version": 1,
        "createdAt": "2026-01-01T00:00:00Z",
        "recordCount": 1,
        "compression": "none",
        "shards": [
            {
                "id": "shard-0001",
                "url": "shards/shard-0001.ndjson",
                "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "records": 1,
                "bytes": 24,
            }
        ],
        "attribution": {
            "source": "Wikipedia content snapshots",
            "license": "CC BY-SA 4.0",
            "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
            "requiredNotice": "Wikipedia is a trademark of the Wikimedia Foundation.",
        },
    }
    (pack_dir / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")

    out_dir = tmp_path / "site" / "packs" / "en-core-1m" / "v1"
    latest = tmp_path / "site" / "packs" / "en-core-1m" / "latest.json"
    result = publish_pack(
        pack_dir=pack_dir,
        output_dir=out_dir,
        base_url="https://example.org/packs/en-core-1m/v1",
        latest_pointer=latest,
        clean=True,
    )

    assert result["packId"] == "en-core-1m"
    assert (out_dir / "shards" / "shard-0001.ndjson").exists()
    published = json.loads((out_dir / "manifest.json").read_text(encoding="utf-8"))
    assert published["shards"][0]["url"] == "https://example.org/packs/en-core-1m/v1/shards/shard-0001.ndjson"
    latest_payload = json.loads(latest.read_text(encoding="utf-8"))
    assert latest_payload["manifestUrl"] == "https://example.org/packs/en-core-1m/v1/manifest.json"
