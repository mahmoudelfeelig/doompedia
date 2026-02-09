import gzip
import json
from pathlib import Path

from doompedia_pipeline.build_en_1m_from_sql import (
    _parse_sql_values,
    build_cards,
    collect_candidate_pages,
)


def test_parse_sql_values_handles_quotes_and_nulls() -> None:
    rows = _parse_sql_values("(1,0,'Ada_Lovelace','',0),(2,0,'O\\'Connor',NULL,1)")
    assert rows[0][0] == "1"
    assert rows[0][2] == "Ada_Lovelace"
    assert rows[1][2] == "O'Connor"
    assert rows[1][3] is None


def test_build_cards_from_small_sql_dumps(tmp_path: Path) -> None:
    page_sql = tmp_path / "page.sql.gz"
    props_sql = tmp_path / "page_props.sql.gz"
    out = tmp_path / "cards.ndjson"

    with gzip.open(page_sql, "wt", encoding="utf-8") as fh:
        fh.write(
            "INSERT INTO `page` VALUES "
            "(1,0,'Ada_Lovelace','',0,0,0,'',NULL,0,0,'wikitext',NULL),"
            "(2,0,'Grace_Hopper','',0,0,0,'',NULL,0,0,'wikitext',NULL),"
            "(3,1,'Talk:Ada','',0,0,0,'',NULL,0,0,'wikitext',NULL),"
            "(4,0,'Redirect_Page','',1,0,0,'',NULL,0,0,'wikitext',NULL);\n"
        )

    with gzip.open(props_sql, "wt", encoding="utf-8") as fh:
        fh.write(
            "INSERT INTO `page_props` VALUES "
            "(1,'wikibase-shortdesc','English mathematician and writer known for early mechanical computation',NULL),"
            "(2,'wikibase-shortdesc','American computer scientist and rear admiral in the U.S. Navy',NULL),"
            "(2,'displaytitle','Grace Hopper',NULL),"
            "(4,'wikibase-shortdesc','Redirect should be filtered',NULL);\n"
        )

    candidates = collect_candidate_pages(page_sql, target=2, oversample=2.0)
    assert [page_id for page_id, _ in candidates][:2] == [1, 2]

    summary = build_cards(
        page_sql_gz=page_sql,
        page_props_sql_gz=props_sql,
        output_ndjson=out,
        language="en",
        target=2,
        oversample=2.0,
    )
    assert summary["written"] == 2

    rows = [json.loads(line) for line in out.read_text(encoding="utf-8").splitlines() if line.strip()]
    assert len(rows) == 2
    assert rows[0]["title"] == "Ada Lovelace"
    assert rows[0]["wiki_url"].startswith("https://en.wikipedia.org/wiki/")
