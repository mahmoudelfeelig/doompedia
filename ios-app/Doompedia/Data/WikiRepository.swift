import Foundation

final class WikiRepository {
    private let store: SQLiteStore
    private let config: RankingConfig
    private let ranker: FeedRanker
    static let defaultBookmarksFolderID: Int64 = 1

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

    func recordOpen(card: ArticleCard, level: PersonalizationLevel) throws {
        try store.insertHistory(pageId: card.pageId, topicKey: card.topicKey)

        let levelFactor = config.personalization.levels[level.rawValue] ?? 0.0
        guard levelFactor > 0 else { return }

        let openRate = config.personalization.learningRates["open"] ?? 0
        try updateAffinity(language: card.lang, topic: card.topicKey, delta: openRate * levelFactor)
    }

    func recordLessLike(card: ArticleCard, level: PersonalizationLevel) throws {
        let levelFactor = config.personalization.levels[level.rawValue] ?? 0.0
        guard levelFactor > 0 else { return }

        let hideRate = config.personalization.learningRates["hide"] ?? -0.5
        try updateAffinity(language: card.lang, topic: card.topicKey, delta: hideRate * levelFactor)
    }

    func recordMoreLike(card: ArticleCard, level: PersonalizationLevel) throws {
        let levelFactor = config.personalization.levels[level.rawValue] ?? 0.0
        guard levelFactor > 0 else { return }

        let likeRate = config.personalization.learningRates["like"]
            ?? config.personalization.learningRates["bookmark"]
            ?? 0.7
        try updateAffinity(language: card.lang, topic: card.topicKey, delta: likeRate * levelFactor)
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

    func savedCards(folderID: Int64) throws -> [ArticleCard] {
        try store.savedCards(folderID: folderID)
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
}
