import json
from pathlib import Path

from doompedia_pipeline.extract_dump import extract_dump, extract_summary, extract_topic_key


def test_extract_summary_and_topic() -> None:
    wikitext = """
    {{Infobox person}}
    '''Ada Lovelace''' was an English mathematician and writer known for her work on Charles Babbage's early mechanical computer.

    [[Category:Computer scientists]]
    """
    summary = extract_summary(wikitext, min_summary=40, max_summary=320)
    assert summary is not None
    assert "Ada Lovelace" in summary
    assert extract_topic_key(wikitext) == "computer-scientists"


def test_extract_dump_filters_and_writes_cards(tmp_path: Path) -> None:
    xml_path = tmp_path / "sample.xml"
    out_path = tmp_path / "cards.ndjson"
    xml_path.write_text(
        """
        <mediawiki>
          <page>
            <title>Ada Lovelace</title>
            <ns>0</ns>
            <id>1</id>
            <revision>
              <id>11</id>
              <timestamp>2026-01-01T00:00:00Z</timestamp>
              <text>{{Infobox}}'''Ada Lovelace''' was an English mathematician and writer known for work on early mechanical computation.

[[Category:Computer scientists]]</text>
            </revision>
          </page>
          <page>
            <title>Talk:Ada Lovelace</title>
            <ns>1</ns>
            <id>2</id>
            <revision>
              <id>22</id>
              <timestamp>2026-01-01T00:00:00Z</timestamp>
              <text>Talk page text</text>
            </revision>
          </page>
          <page>
            <title>Ada Redirect</title>
            <ns>0</ns>
            <id>3</id>
            <redirect title="Ada Lovelace" />
            <revision>
              <id>33</id>
              <timestamp>2026-01-01T00:00:00Z</timestamp>
              <text>#REDIRECT [[Ada Lovelace]]</text>
            </revision>
          </page>
        </mediawiki>
        """,
        encoding="utf-8",
    )

    result = extract_dump(
        input_path=xml_path,
        output_path=out_path,
        language="en",
        max_records=100,
        min_summary=40,
        max_summary=320,
    )
    assert result["written"] == 1

    lines = [json.loads(line) for line in out_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    assert len(lines) == 1
    assert lines[0]["page_id"] == 1
    assert lines[0]["source_rev_id"] == 11
    assert lines[0]["updated_at"] == "2026-01-01T00:00:00Z"
