from doompedia_pipeline.normalize import clamp_summary, normalize_title


def test_normalize_title_nfkc_casefold() -> None:
    assert normalize_title("  Café  Noir  ") == "café noir"


def test_clamp_summary_behavior() -> None:
    assert clamp_summary("short") is None
    long_summary = "x" * 500
    clamped = clamp_summary(long_summary)
    assert clamped is not None
    assert len(clamped) <= 320
