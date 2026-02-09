-- Offline content schema shared by Android and iOS clients.
-- The content DB is read-mostly and replaced via shard or delta updates.

PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS articles (
    page_id INTEGER PRIMARY KEY,
    lang TEXT NOT NULL,
    title TEXT NOT NULL,
    normalized_title TEXT NOT NULL,
    summary TEXT NOT NULL,
    wiki_url TEXT NOT NULL,
    topic_key TEXT NOT NULL,
    quality_score REAL NOT NULL DEFAULT 0.5,
    is_disambiguation INTEGER NOT NULL DEFAULT 0,
    source_rev_id INTEGER,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS aliases (
    alias_id INTEGER PRIMARY KEY AUTOINCREMENT,
    page_id INTEGER NOT NULL,
    lang TEXT NOT NULL,
    alias TEXT NOT NULL,
    normalized_alias TEXT NOT NULL,
    FOREIGN KEY (page_id) REFERENCES articles(page_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS topics (
    topic_key TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS article_topics (
    page_id INTEGER NOT NULL,
    topic_key TEXT NOT NULL,
    weight REAL NOT NULL,
    PRIMARY KEY (page_id, topic_key),
    FOREIGN KEY (page_id) REFERENCES articles(page_id) ON DELETE CASCADE,
    FOREIGN KEY (topic_key) REFERENCES topics(topic_key) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS pack_meta (
    meta_key TEXT PRIMARY KEY,
    meta_value TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_articles_lang_norm_title
    ON articles(lang, normalized_title);

CREATE INDEX IF NOT EXISTS idx_articles_lang_topic
    ON articles(lang, topic_key);

CREATE INDEX IF NOT EXISTS idx_articles_lang_quality
    ON articles(lang, quality_score DESC);

CREATE INDEX IF NOT EXISTS idx_aliases_lang_norm_alias
    ON aliases(lang, normalized_alias);

CREATE INDEX IF NOT EXISTS idx_article_topics_topic
    ON article_topics(topic_key, page_id);
