from pathlib import Path

from doompedia_pipeline.build_featured_starter import content_output_dir


def test_content_output_dir_appends_content_to_assets_root() -> None:
    assert content_output_dir(Path("/tmp/android-assets")) == Path("/tmp/android-assets/content")


def test_content_output_dir_accepts_content_directory() -> None:
    assert content_output_dir(Path("/tmp/android-assets/content")) == Path("/tmp/android-assets/content")
