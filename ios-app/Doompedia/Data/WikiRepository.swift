import Foundation

final class WikiRepository {
    private let store: SQLiteStore
    private let config: RankingConfig
    private let ranker: FeedRanker
    static let defaultBookmarksFolderID: Int64 = 1
    static let defaultReadFolderID: Int64 = 2

    init(store: SQLiteStore, config: RankingConfig) {
        self.store = store
        self.config = config
        self.ranker = FeedRanker(config: config)
    }

    func bootstrap() throws {
        try SeedBootstrapper.ensureSeedData(store: store)
        try store.ensureSaveDefaults()
    }

    func loadFeed(language: String, level: PersonalizationLevel, limit: Int = 50) throws -> [RankedCard] {
        let candidates = try store.feedCandidates(language: language, limit: 250)
        let affinities = try store.topicAffinities(language: language)
        let recentTopics = try store.recentTopics(limit: config.guardrails.windowSize)
        return ranker.rank(
            candidates: candidates,
            topicAffinity: affinities,
            recentlySeenTopics: recentTopics,
            level: level,
            limit: limit
        )
    }

    func rankCandidates(
        language: String,
        level: PersonalizationLevel,
        candidates: [ArticleCard],
        limit: Int = 50
    ) throws -> [RankedCard] {
        let affinities = try store.topicAffinities(language: language)
        let recentTopics = try store.recentTopics(limit: config.guardrails.windowSize)
        return ranker.rank(
            candidates: candidates,
            topicAffinity: affinities,
            recentlySeenTopics: recentTopics,
            level: level,
            limit: limit
        )
    }

    func search(language: String, query: String) throws -> [ArticleCard] {
        let normalized = normalizeSearch(query)
        if normalized.isEmpty { return [] }

        let maxResults = config.search.maxResults
        let exact = try store.searchExactTitle(language: language, normalizedQuery: normalized, limit: maxResults)
        let prefix = try store.searchTitlePrefix(language: language, normalizedPrefix: normalized, limit: maxResults)
        let alias = try store.searchAlias(language: language, normalizedQuery: normalized, normalizedPrefix: normalized, limit: maxResults)

        var seen: Set<Int64> = []
        var ordered: [ArticleCard] = []
        for row in (exact + prefix + alias) where seen.insert(row.pageId).inserted {
            ordered.append(row)
        }

        if normalized.count >= config.search.typoMinQueryLength,
           let firstChar = normalized.first {
            let minLen = normalized.count - config.search.typoDistance
            let maxLen = normalized.count + config.search.typoDistance
            let candidates = try store.typoCandidates(
                language: language,
                firstChar: String(firstChar),
                minLen: minLen,
                maxLen: maxLen,
                limit: config.search.maxTypoCandidates
            )

            for candidate in candidates where !seen.contains(candidate.pageId) {
                if editDistanceAtMostOne(normalized, candidate.normalizedTitle) {
                    seen.insert(candidate.pageId)
                    ordered.append(candidate)
                }
            }
        }

        return Array(ordered.prefix(maxResults))
    }

    func cacheOnlineArticles(_ rows: [SeedRow]) throws {
        guard !rows.isEmpty else { return }
        try store.upsertSeedRows(rows)
    }

    func recordOpen(card: ArticleCard, level: PersonalizationLevel) throws {
        let preferenceKeys = CardKeywords.preferenceKeys(
            title: card.title,
            summary: card.summary,
            topicKey: card.topicKey,
            maxKeys: 10
        )
        let primaryTopic = CardKeywords.primaryTopic(
            title: card.title,
            summary: card.summary,
            topicKey: card.topicKey
        )

        try store.insertHistory(pageId: card.pageId, topicKey: primaryTopic)

        let levelFactor = config.personalization.levels[level.rawValue] ?? 0.0
        guard levelFactor > 0 else { return }

        let openRate = config.personalization.learningRates["open"] ?? 0
        try updateAffinities(
            language: card.lang,
            topics: preferenceKeys,
            delta: openRate * levelFactor
        )
    }

    func recordLessLike(card: ArticleCard, level: PersonalizationLevel) throws {
        let levelFactor = config.personalization.levels[level.rawValue] ?? 0.0
        guard levelFactor > 0 else { return }

        let preferenceKeys = CardKeywords.preferenceKeys(
            title: card.title,
            summary: card.summary,
            topicKey: card.topicKey,
            maxKeys: 10
        )
        let hideRate = config.personalization.learningRates["hide"] ?? -0.5
        try updateAffinities(
            language: card.lang,
            topics: preferenceKeys,
            delta: hideRate * levelFactor
        )
    }

    func recordMoreLike(card: ArticleCard, level: PersonalizationLevel) throws {
        let levelFactor = config.personalization.levels[level.rawValue] ?? 0.0
        guard levelFactor > 0 else { return }

        let preferenceKeys = CardKeywords.preferenceKeys(
            title: card.title,
            summary: card.summary,
            topicKey: card.topicKey,
            maxKeys: 10
        )
        let likeRate = config.personalization.learningRates["like"]
            ?? config.personalization.learningRates["bookmark"]
            ?? 0.7
        try updateAffinities(
            language: card.lang,
            topics: preferenceKeys,
            delta: likeRate * levelFactor
        )
    }

    func toggleBookmark(pageId: Int64) throws -> Bool {
        try store.toggleBookmark(pageId: pageId)
    }

    func saveFolders() throws -> [SaveFolderSummary] {
        try store.saveFoldersWithCounts()
    }

    func createFolder(name: String) throws -> Bool {
        try store.createFolder(name: name)
    }

    func deleteFolder(folderID: Int64) throws -> Bool {
        try store.deleteFolder(folderID: folderID)
    }

    func selectedFolderIDs(pageID: Int64) throws -> Set<Int64> {
        try store.folderIDsForArticle(pageID: pageID)
    }

    func setFoldersForArticle(pageID: Int64, folderIDs: Set<Int64>) throws {
        try store.setFoldersForArticle(pageID: pageID, folderIDs: folderIDs)
    }

    func savedCards(folderID: Int64, language: String, readSort: ReadSort = .newestFirst) throws -> [ArticleCard] {
        try store.savedCards(folderID: folderID, language: language, readSort: readSort)
    }

    func exportFolders(selectedFolderIDs: Set<Int64>? = nil, language: String) throws -> String {
        let folders = try saveFolders()
            .filter { $0.folderId != Self.defaultReadFolderID }
            .filter { selectedFolderIDs == nil || selectedFolderIDs?.contains($0.folderId) == true }

        let payloadFolders: [[String: Any]] = try folders.map { folder in
            let cards = try savedCards(folderID: folder.folderId, language: language)
            return [
                "folderId": folder.folderId,
                "name": folder.name,
                "isDefault": folder.isDefault,
                "articlePageIds": cards.map(\.pageId),
            ]
        }

        let root: [String: Any] = [
            "version": 1,
            "format": "doompedia-folder-export",
            "exportedAt": ISO8601DateFormatter().string(from: Date()),
            "folders": payloadFolders,
        ]
        let data = try JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys])
        return String(decoding: data, as: UTF8.self)
    }

    func importFolders(payload: String) throws -> Int {
        guard
            let data = payload.data(using: .utf8),
            let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let folders = root["folders"] as? [[String: Any]]
        else {
            throw NSError(domain: "Doompedia", code: 801, userInfo: [NSLocalizedDescriptionKey: "Invalid folder JSON"])
        }

        var linked = 0
        for folder in folders {
            guard let name = (folder["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !name.isEmpty else { continue }

            let targetFolderID: Int64
            if name.caseInsensitiveCompare("Bookmarks") == .orderedSame {
                targetFolderID = Self.defaultBookmarksFolderID
            } else if name.caseInsensitiveCompare("Read") == .orderedSame {
                targetFolderID = Self.defaultReadFolderID
            } else {
                _ = try createFolder(name: name)
                let resolved = try saveFolders().first { $0.name.caseInsensitiveCompare(name) == .orderedSame }?.folderId
                guard let resolved else { continue }
                targetFolderID = resolved
            }

            let ids = (folder["articlePageIds"] as? [NSNumber])?.map { $0.int64Value } ?? []
            for pageID in ids {
                let existing = try selectedFolderIDs(pageID: pageID)
                let merged = existing.union([targetFolderID])
                try setFoldersForArticle(pageID: pageID, folderIDs: merged)
                linked += 1
            }
        }
        return linked
    }

    private func updateAffinity(language: String, topic: String, delta: Double) throws {
        let map = try store.topicAffinities(language: language)
        let current = map[topic] ?? 0
        let boundedDelta = max(
            -config.personalization.dailyDriftCap,
            min(config.personalization.dailyDriftCap, delta)
        )
        let minClamp = config.personalization.topicClamp.min
        let maxClamp = config.personalization.topicClamp.max
        let next = max(minClamp, min(maxClamp, current + boundedDelta))
        try store.upsertTopicAffinity(language: language, topicKey: topic, score: next)
    }

    private func updateAffinities(language: String, topics: [String], delta: Double) throws {
        let uniqueTopics = Array(
            Set(
                topics
                    .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
                    .filter { !$0.isEmpty }
            )
        ).prefix(10)
        guard !uniqueTopics.isEmpty else { return }

        let topicsArray = Array(uniqueTopics)
        let primary = topicsArray.first!
        let secondary = Array(topicsArray.dropFirst())
        let primaryDelta = delta * 0.55
        let secondaryDelta = secondary.isEmpty ? 0 : (delta * 0.45 / Double(secondary.count))

        try updateAffinity(language: language, topic: primary, delta: primaryDelta)
        for topic in secondary {
            try updateAffinity(language: language, topic: topic, delta: secondaryDelta)
        }
    }
}
