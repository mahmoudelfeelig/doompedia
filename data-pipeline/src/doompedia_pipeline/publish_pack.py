from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Publish a generated pack directory for static hosting")
    parser.add_argument("--pack-dir", required=True, help="Input pack directory containing manifest.json and shards/")
    parser.add_argument("--output-dir", required=True, help="Output directory for hosted pack files")
    parser.add_argument(
        "--base-url",
        default="",
        help="Optional absolute base URL used to rewrite manifest shard/delta URLs",
    )
    parser.add_argument(
        "--latest-pointer",
        default="",
        help="Optional path to write latest.json with manifest URL metadata",
    )
    parser.add_argument("--clean", action="store_true", help="Delete output directory before publishing")
    return parser.parse_args()


def _resolve_pack_path(pack_dir: Path, relative_path: str) -> Path:
    direct = pack_dir / relative_path
    if direct.exists():
        return direct
    fallback = pack_dir / relative_path.split("/")[-1]
    if fallback.exists():
        return fallback
    return direct


def publish_pack(
    pack_dir: Path,
    output_dir: Path,
    base_url: str = "",
    latest_pointer: Path | None = None,
    clean: bool = False,
) -> dict[str, object]:
    manifest_path = pack_dir / "manifest.json"
    if not manifest_path.exists():
        raise FileNotFoundError(f"Manifest not found: {manifest_path}")

    if clean and output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    shards_out = output_dir / "shards"
    shards_out.mkdir(parents=True, exist_ok=True)

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    base_url_norm = base_url.rstrip("/")

    for shard in manifest.get("shards", []):
        source = _resolve_pack_path(pack_dir, shard["url"])
        if not source.exists():
            raise FileNotFoundError(f"Shard not found: {source}")
        file_name = source.name
        destination = shards_out / file_name
        shutil.copy2(source, destination)
        shard["url"] = f"{base_url_norm}/shards/{file_name}" if base_url_norm else f"shards/{file_name}"

    delta = manifest.get("delta")
    if isinstance(delta, dict) and "url" in delta:
        delta_source = _resolve_pack_path(pack_dir, str(delta["url"]))
        if delta_source.exists():
            delta_name = delta_source.name
            shutil.copy2(delta_source, output_dir / delta_name)
            delta["url"] = f"{base_url_norm}/{delta_name}" if base_url_norm else delta_name

    published_manifest_path = output_dir / "manifest.json"
    published_manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    checksums = pack_dir / "checksums.txt"
    if checksums.exists():
        shutil.copy2(checksums, output_dir / "checksums.txt")

    if latest_pointer is not None:
        latest_pointer.parent.mkdir(parents=True, exist_ok=True)
        manifest_url = f"{base_url_norm}/manifest.json" if base_url_norm else "manifest.json"
        latest_payload = {
            "packId": manifest.get("packId"),
            "version": manifest.get("version"),
            "manifestUrl": manifest_url,
        }
        latest_pointer.write_text(
            json.dumps(latest_payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

    return {
        "packId": manifest.get("packId"),
        "version": manifest.get("version"),
        "shards": len(manifest.get("shards", [])),
        "outputDir": str(output_dir),
        "baseUrl": base_url_norm,
    }


def main() -> None:
    args = parse_args()
    latest_pointer = Path(args.latest_pointer) if args.latest_pointer.strip() else None
    result = publish_pack(
        pack_dir=Path(args.pack_dir),
        output_dir=Path(args.output_dir),
        base_url=args.base_url,
        latest_pointer=latest_pointer,
        clean=args.clean,
    )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
