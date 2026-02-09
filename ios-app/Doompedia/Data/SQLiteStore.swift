import Foundation
import SQLite3

enum SQLiteStoreError: Error {
    case openDatabase(String)
    case prepare(String)
    case execute(String)
}

struct SeedRow: Codable {
    let page_id: Int64
    let lang: String
    let title: String
    let summary: String
    let wiki_url: String
    let topic_key: String
    let quality_score: Double
    let is_disambiguation: Bool
    let source_rev_id: Int64?
    let updated_at: String
    let aliases: [String]
}

final class SQLiteStore {
    private var db: OpaquePointer?
    private let queue = DispatchQueue(label: "wiki.scroll.sqlite.queue")

    init(filename: String = "doompedia.sqlite") throws {
        let url = try Self.databaseURL(filename: filename)
        if sqlite3_open(url.path, &db) != SQLITE_OK {
            throw SQLiteStoreError.openDatabase(lastError())
        }
        try migrate()
    }

    deinit {
        sqlite3_close(db)
    }

    func articleCount() throws -> Int {
        try queue.sync {
            try scalarInt(sql: "SELECT COUNT(*) FROM articles")
        }
    }

    func upsertSeedRows(_ rows: [SeedRow]) throws {
        try queue.sync {
            try execute(sql: "BEGIN TRANSACTION")
            do {
                for row in rows {
                    try execute(
                        sql: """
                        INSERT INTO articles (
                            page_id, lang, title, normalized_title, summary, wiki_url, topic_key,
                            quality_score, is_disambiguation, source_rev_id, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(page_id) DO UPDATE SET
                            lang = excluded.lang,
                            title = excluded.title,
                            normalized_title = excluded.normalized_title,
                            summary = excluded.summary,
                            wiki_url = excluded.wiki_url,
                            topic_key = excluded.topic_key,
                            quality_score = excluded.quality_score,
                            is_disambiguation = excluded.is_disambiguation,
                            source_rev_id = excluded.source_rev_id,
                            updated_at = excluded.updated_at
                        """,
                        bindings: [
                            .int64(row.page_id),
                            .text(row.lang),
                            .text(row.title),
                            .text(normalizeSearch(row.title)),
                            .text(row.summary),
                            .text(row.wiki_url),
                            .text(row.topic_key),
                            .double(row.quality_score),
                            .int64(row.is_disambiguation ? 1 : 0),
                            row.source_rev_id.map(SQLiteValue.int64) ?? .null,
                            .text(row.updated_at),
                        ]
                    )

                    try execute(sql: "DELETE FROM aliases WHERE page_id = ?", bindings: [.int64(row.page_id)])
                    for alias in row.aliases {
                        try execute(
                            sql: "INSERT INTO aliases (page_id, lang, alias, normalized_alias) VALUES (?, ?, ?, ?)",
                            bindings: [.int64(row.page_id), .text(row.lang), .text(alias), .text(normalizeSearch(alias))]
                        )
                    }
                }
                try execute(sql: "COMMIT")
            } catch {
                _ = try? execute(sql: "ROLLBACK")
                throw error
            }
        }
    }

    func feedCandidates(language: String, limit: Int) throws -> [ArticleCard] {
        try queryCards(
            sql: selectCardColumns + """
            WHERE a.lang = ? AND a.is_disambiguation = 0
            ORDER BY a.quality_score DESC, a.page_id ASC
            LIMIT ?
            """,
            bindings: [.text(language), .int64(Int64(limit))]
        )
    }

    func searchExactTitle(language: String, normalizedQuery: String, limit: Int) throws -> [ArticleCard] {
        try queryCards(
            sql: selectCardColumns + """
            WHERE a.lang = ? AND a.normalized_title = ?
            LIMIT ?
            """,
            bindings: [.text(language), .text(normalizedQuery), .int64(Int64(limit))]
        )
    }

    func searchTitlePrefix(language: String, normalizedPrefix: String, limit: Int) throws -> [ArticleCard] {
        try queryCards(
            sql: selectCardColumns + """
            WHERE a.lang = ? AND a.normalized_title LIKE ?
            ORDER BY a.normalized_title ASC
            LIMIT ?
            """,
            bindings: [.text(language), .text("\(normalizedPrefix)%"), .int64(Int64(limit))]
        )
    }

    func searchAlias(language: String, normalizedQuery: String, normalizedPrefix: String, limit: Int) throws -> [ArticleCard] {
        try queryCards(
            sql: """
            SELECT a.page_id, a.lang, a.title, a.normalized_title, a.summary, a.wiki_url,
                   a.topic_key, a.quality_score, a.is_disambiguation, a.source_rev_id, a.updated_at,
                   CASE WHEN b.page_id IS NULL THEN 0 ELSE 1 END AS bookmarked
            FROM aliases alias
            JOIN articles a ON a.page_id = alias.page_id
            LEFT JOIN bookmarks b ON b.page_id = a.page_id
            WHERE alias.lang = ?
              AND (alias.normalized_alias = ? OR alias.normalized_alias LIKE ?)
            ORDER BY alias.normalized_alias ASC
            LIMIT ?
            """,
            bindings: [.text(language), .text(normalizedQuery), .text("\(normalizedPrefix)%"), .int64(Int64(limit))]
        )
    }

    func typoCandidates(language: String, firstChar: String, minLen: Int, maxLen: Int, limit: Int) throws -> [ArticleCard] {
        try queryCards(
            sql: selectCardColumns + """
            WHERE a.lang = ?
              AND substr(a.normalized_title, 1, 1) = ?
              AND length(a.normalized_title) BETWEEN ? AND ?
            ORDER BY a.quality_score DESC
            LIMIT ?
            """,
            bindings: [
                .text(language),
                .text(firstChar),
                .int64(Int64(minLen)),
                .int64(Int64(maxLen)),
                .int64(Int64(limit)),
            ]
        )
    }

    func recentTopics(limit: Int) throws -> [String] {
        try queue.sync {
            var values: [String] = []
            let sql = "SELECT topic_key FROM history ORDER BY opened_at DESC LIMIT ?"
            var statement: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else {
                throw SQLiteStoreError.prepare(lastError())
            }
            defer { sqlite3_finalize(statement) }

            bind(.int64(Int64(limit)), to: statement, index: 1)
            while sqlite3_step(statement) == SQLITE_ROW {
                if let cString = sqlite3_column_text(statement, 0) {
                    values.append(String(cString: cString))
                }
            }
            return values
        }
    }

    func topicAffinities(language: String) throws -> [String: Double] {
        try queue.sync {
            var map: [String: Double] = [:]
            let sql = "SELECT topic_key, score FROM topic_affinity WHERE lang = ?"
            var statement: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else {
                throw SQLiteStoreError.prepare(lastError())
            }
            defer { sqlite3_finalize(statement) }

            bind(.text(language), to: statement, index: 1)
            while sqlite3_step(statement) == SQLITE_ROW {
                guard let cTopic = sqlite3_column_text(statement, 0) else { continue }
                let topic = String(cString: cTopic)
                let score = sqlite3_column_double(statement, 1)
                map[topic] = score
            }
            return map
        }
    }

    func upsertTopicAffinity(language: String, topicKey: String, score: Double) throws {
        try queue.sync {
            try execute(
                sql: """
                INSERT INTO topic_affinity (lang, topic_key, score, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(lang, topic_key) DO UPDATE SET
                    score = excluded.score,
                    updated_at = excluded.updated_at
                """,
                bindings: [
                    .text(language),
                    .text(topicKey),
                    .double(score),
                    .int64(Int64(Date().timeIntervalSince1970 * 1000)),
                ]
            )
        }
    }

    func insertHistory(pageId: Int64, topicKey: String) throws {
        try queue.sync {
            try execute(
                sql: "INSERT INTO history (page_id, topic_key, opened_at) VALUES (?, ?, ?)",
                bindings: [
                    .int64(pageId),
                    .text(topicKey),
                    .int64(Int64(Date().timeIntervalSince1970 * 1000)),
                ]
            )
        }
    }

    func toggleBookmark(pageId: Int64) throws -> Bool {
        try queue.sync {
            let exists = try scalarInt(sql: "SELECT COUNT(*) FROM bookmarks WHERE page_id = ?", bindings: [.int64(pageId)]) > 0
            if exists {
                try execute(sql: "DELETE FROM bookmarks WHERE page_id = ?", bindings: [.int64(pageId)])
                return false
            }
            try execute(
                sql: "INSERT INTO bookmarks (page_id, created_at) VALUES (?, ?)",
                bindings: [.int64(pageId), .int64(Int64(Date().timeIntervalSince1970 * 1000))]
            )
            return true
        }
    }

    func deleteArticles(pageIDs: [Int64]) throws {
        guard !pageIDs.isEmpty else { return }
        try queue.sync {
            try execute(sql: "BEGIN TRANSACTION")
            do {
                for pageID in pageIDs {
                    try execute(sql: "DELETE FROM articles WHERE page_id = ?", bindings: [.int64(pageID)])
                }
                try execute(sql: "COMMIT")
            } catch {
                _ = try? execute(sql: "ROLLBACK")
                throw error
            }
        }
    }

    private static func databaseURL(filename: String) throws -> URL {
        let directory = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return directory.appendingPathComponent(filename)
    }

    private func migrate() throws {
        let statements = [
            """
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
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS aliases (
                alias_id INTEGER PRIMARY KEY AUTOINCREMENT,
                page_id INTEGER NOT NULL,
                lang TEXT NOT NULL,
                alias TEXT NOT NULL,
                normalized_alias TEXT NOT NULL,
                FOREIGN KEY(page_id) REFERENCES articles(page_id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS bookmarks (
                page_id INTEGER PRIMARY KEY,
                created_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                page_id INTEGER NOT NULL,
                topic_key TEXT NOT NULL,
                opened_at INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS topic_affinity (
                lang TEXT NOT NULL,
                topic_key TEXT NOT NULL,
                score REAL NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(lang, topic_key)
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_articles_lang_norm_title ON articles(lang, normalized_title)",
            "CREATE INDEX IF NOT EXISTS idx_articles_lang_topic ON articles(lang, topic_key)",
            "CREATE INDEX IF NOT EXISTS idx_aliases_lang_norm_alias ON aliases(lang, normalized_alias)",
            "CREATE INDEX IF NOT EXISTS idx_history_opened_at ON history(opened_at)",
        ]

        try queue.sync {
            for sql in statements {
                try execute(sql: sql)
            }
        }
    }

    private func queryCards(sql: String, bindings: [SQLiteValue]) throws -> [ArticleCard] {
        try queue.sync {
            var rows: [ArticleCard] = []
            var statement: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else {
                throw SQLiteStoreError.prepare(lastError())
            }
            defer { sqlite3_finalize(statement) }

            for (index, value) in bindings.enumerated() {
                bind(value, to: statement, index: Int32(index + 1))
            }

            while sqlite3_step(statement) == SQLITE_ROW {
                rows.append(parseCard(from: statement))
            }
            return rows
        }
    }

    private func parseCard(from statement: OpaquePointer?) -> ArticleCard {
        let pageId = sqlite3_column_int64(statement, 0)
        let lang = sqliteString(statement, 1)
        let title = sqliteString(statement, 2)
        let normalizedTitle = sqliteString(statement, 3)
        let summary = sqliteString(statement, 4)
        let wikiURL = sqliteString(statement, 5)
        let topicKey = sqliteString(statement, 6)
        let qualityScore = sqlite3_column_double(statement, 7)
        let isDisambiguation = sqlite3_column_int(statement, 8) != 0
        let sourceRev = sqlite3_column_type(statement, 9) == SQLITE_NULL ? nil : sqlite3_column_int64(statement, 9)
        let updatedAt = sqliteString(statement, 10)
        let bookmarked = sqlite3_column_int(statement, 11) != 0

        return ArticleCard(
            pageId: pageId,
            lang: lang,
            title: title,
            normalizedTitle: normalizedTitle,
            summary: summary,
            wikiURL: wikiURL,
            topicKey: topicKey,
            qualityScore: qualityScore,
            isDisambiguation: isDisambiguation,
            sourceRevId: sourceRev,
            updatedAt: updatedAt,
            bookmarked: bookmarked
        )
    }

    private func scalarInt(sql: String, bindings: [SQLiteValue] = []) throws -> Int {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else {
            throw SQLiteStoreError.prepare(lastError())
        }
        defer { sqlite3_finalize(statement) }

        for (index, value) in bindings.enumerated() {
            bind(value, to: statement, index: Int32(index + 1))
        }

        guard sqlite3_step(statement) == SQLITE_ROW else {
            throw SQLiteStoreError.execute(lastError())
        }

        return Int(sqlite3_column_int(statement, 0))
    }

    private func execute(sql: String, bindings: [SQLiteValue] = []) throws {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &statement, nil) == SQLITE_OK else {
            throw SQLiteStoreError.prepare(lastError())
        }
        defer { sqlite3_finalize(statement) }

        for (index, value) in bindings.enumerated() {
            bind(value, to: statement, index: Int32(index + 1))
        }

        guard sqlite3_step(statement) == SQLITE_DONE else {
            throw SQLiteStoreError.execute(lastError())
        }
    }

    private func bind(_ value: SQLiteValue, to statement: OpaquePointer?, index: Int32) {
        switch value {
        case let .int64(v):
            sqlite3_bind_int64(statement, index, v)
        case let .double(v):
            sqlite3_bind_double(statement, index, v)
        case let .text(v):
            sqlite3_bind_text(statement, index, v, -1, SQLITE_TRANSIENT)
        case .null:
            sqlite3_bind_null(statement, index)
        }
    }

    private func sqliteString(_ statement: OpaquePointer?, _ index: Int32) -> String {
        guard let value = sqlite3_column_text(statement, index) else {
            return ""
        }
        return String(cString: value)
    }

    private func lastError() -> String {
        if let cString = sqlite3_errmsg(db) {
            return String(cString: cString)
        }
        return "Unknown SQLite error"
    }
}

private let selectCardColumns = """
SELECT a.page_id, a.lang, a.title, a.normalized_title, a.summary, a.wiki_url,
       a.topic_key, a.quality_score, a.is_disambiguation, a.source_rev_id, a.updated_at,
       CASE WHEN b.page_id IS NULL THEN 0 ELSE 1 END AS bookmarked
FROM articles a
LEFT JOIN bookmarks b ON b.page_id = a.page_id
"""

enum SQLiteValue {
    case int64(Int64)
    case double(Double)
    case text(String)
    case null
}

private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
