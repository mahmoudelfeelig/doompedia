package com.feelbachelor.doompedia.data.repo

import com.feelbachelor.doompedia.data.db.ArticleWithBookmark
import com.feelbachelor.doompedia.data.db.ArticleFolderRefEntity
import com.feelbachelor.doompedia.data.db.BookmarkEntity
import com.feelbachelor.doompedia.data.db.HistoryEntity
import com.feelbachelor.doompedia.data.db.SaveFolderEntity
import com.feelbachelor.doompedia.data.db.SaveFolderSummaryRow
import com.feelbachelor.doompedia.data.db.TopicAffinityEntity
import com.feelbachelor.doompedia.data.db.WikiDao
import com.feelbachelor.doompedia.data.importer.TopicClassifier
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.RankedCard
import com.feelbachelor.doompedia.domain.SaveFolderSummary
import com.feelbachelor.doompedia.domain.editDistanceAtMostOne
import com.feelbachelor.doompedia.domain.normalizeSearch
import com.feelbachelor.doompedia.ranking.FeedRanker
import com.feelbachelor.doompedia.ranking.RankingConfig

class WikiRepository(
    private val dao: WikiDao,
    private val rankingConfig: RankingConfig,
    private val ranker: FeedRanker,
) {
    companion object {
        const val DEFAULT_BOOKMARKS_FOLDER_ID = 1L
    }

    suspend fun ensureDefaults() {
        dao.ensureDefaultFolder(createdAt = System.currentTimeMillis())
        dao.backfillBookmarksIntoDefaultFolder()
    }

    suspend fun loadFeed(
        language: String,
        personalizationLevel: PersonalizationLevel,
        limit: Int = 50,
    ): List<RankedCard> {
        val candidates = dao.feedCandidates(language, 250).map { it.toDomain() }
        val affinity = dao.topicAffinities(language).associate { it.topicKey to it.score }
        val recentTopics = dao.recentTopics(rankingConfig.guardrails.windowSize)
        return ranker.rank(
            candidates = candidates,
            topicAffinity = affinity,
            recentlySeenTopics = recentTopics,
            level = personalizationLevel,
            limit = limit,
        )
    }

    suspend fun searchByTitle(
        language: String,
        query: String,
    ): List<ArticleCard> {
        val normalized = normalizeSearch(query)
        if (normalized.isBlank()) return emptyList()

        val maxResults = rankingConfig.search.maxResults
        val exact = dao.searchExactTitle(language, normalized, maxResults)
        val prefix = dao.searchTitlePrefix(language, normalized, maxResults)
        val alias = dao.searchAlias(language, normalized, normalized, maxResults)

        val combined = LinkedHashMap<Long, ArticleCard>()
        (exact + prefix + alias).forEach { row -> combined.putIfAbsent(row.pageId, row.toDomain()) }

        if (normalized.length >= rankingConfig.search.typoMinQueryLength) {
            val firstChar = normalized.take(1)
            val minLen = normalized.length - rankingConfig.search.typoDistance
            val maxLen = normalized.length + rankingConfig.search.typoDistance
            val typoCandidates = dao.typoCandidates(
                lang = language,
                firstChar = firstChar,
                minLen = minLen,
                maxLen = maxLen,
                limit = rankingConfig.search.maxTypoCandidates,
            )
            typoCandidates.forEach { row ->
                if (combined.containsKey(row.pageId)) return@forEach
                if (editDistanceAtMostOne(normalized, row.normalizedTitle)) {
                    combined[row.pageId] = row.toDomain()
                }
            }
        }

        return combined.values.take(maxResults)
    }

    suspend fun recordOpen(card: ArticleCard, personalizationLevel: PersonalizationLevel) {
        dao.insertHistory(
            HistoryEntity(
                pageId = card.pageId,
                topicKey = card.topicKey,
                openedAt = System.currentTimeMillis(),
            )
        )

        val levelMultiplier = rankingConfig.personalization.levels[personalizationLevel.name] ?: 0.0
        if (levelMultiplier <= 0.0) return

        val learningRate = rankingConfig.personalization.learningRates["open"] ?: 0.0
        updateTopicAffinity(
            language = card.lang,
            topicKey = card.topicKey,
            delta = learningRate * levelMultiplier,
        )
    }

    suspend fun recordLessLike(card: ArticleCard, personalizationLevel: PersonalizationLevel) {
        val levelMultiplier = rankingConfig.personalization.levels[personalizationLevel.name] ?: 0.0
        if (levelMultiplier <= 0.0) return

        val learningRate = rankingConfig.personalization.learningRates["hide"] ?: -0.5
        updateTopicAffinity(
            language = card.lang,
            topicKey = card.topicKey,
            delta = learningRate * levelMultiplier,
        )
    }

    suspend fun recordMoreLike(card: ArticleCard, personalizationLevel: PersonalizationLevel) {
        val levelMultiplier = rankingConfig.personalization.levels[personalizationLevel.name] ?: 0.0
        if (levelMultiplier <= 0.0) return

        val learningRate = rankingConfig.personalization.learningRates["like"]
            ?: rankingConfig.personalization.learningRates["bookmark"]
            ?: 0.7
        updateTopicAffinity(
            language = card.lang,
            topicKey = card.topicKey,
            delta = learningRate * levelMultiplier,
        )
    }

    suspend fun toggleBookmark(pageId: Long): Boolean {
        ensureDefaults()
        val bookmarked = dao.isBookmarked(pageId)
        if (bookmarked) {
            dao.deleteBookmark(pageId)
            dao.deleteFolderRef(DEFAULT_BOOKMARKS_FOLDER_ID, pageId)
            return false
        }
        val now = System.currentTimeMillis()
        dao.upsertBookmark(
            BookmarkEntity(
                pageId = pageId,
                createdAt = now,
            )
        )
        dao.upsertFolderRefs(
            listOf(
                ArticleFolderRefEntity(
                    folderId = DEFAULT_BOOKMARKS_FOLDER_ID,
                    pageId = pageId,
                    createdAt = now,
                ),
            )
        )
        return true
    }

    suspend fun saveFolders(): List<SaveFolderSummary> {
        ensureDefaults()
        return dao.saveFoldersWithCounts().map { it.toDomain() }
    }

    suspend fun createFolder(name: String): Boolean {
        ensureDefaults()
        val cleaned = name.trim()
        if (cleaned.isBlank()) return false
        val inserted = dao.insertFolder(
            SaveFolderEntity(
                name = cleaned,
                isDefault = false,
                createdAt = System.currentTimeMillis(),
            )
        )
        return inserted != -1L
    }

    suspend fun deleteFolder(folderId: Long): Boolean {
        ensureDefaults()
        if (folderId == DEFAULT_BOOKMARKS_FOLDER_ID) return false
        return dao.deleteFolder(folderId) > 0
    }

    suspend fun savedCards(folderId: Long, limit: Int = 400): List<ArticleCard> {
        ensureDefaults()
        return dao.savedCardsInFolder(folderId = folderId, limit = limit).map { it.toDomain() }
    }

    suspend fun selectedFolderIds(pageId: Long): Set<Long> {
        ensureDefaults()
        return dao.folderIdsForArticle(pageId).toSet()
    }

    suspend fun setFoldersForArticle(pageId: Long, folderIds: Set<Long>) {
        ensureDefaults()
        val now = System.currentTimeMillis()
        dao.clearFolderRefsForArticle(pageId)
        if (folderIds.isNotEmpty()) {
            dao.upsertFolderRefs(
                folderIds.map { folderId ->
                    ArticleFolderRefEntity(
                        folderId = folderId,
                        pageId = pageId,
                        createdAt = now,
                    )
                }
            )
        }

        if (DEFAULT_BOOKMARKS_FOLDER_ID in folderIds) {
            dao.upsertBookmark(
                BookmarkEntity(
                    pageId = pageId,
                    createdAt = now,
                )
            )
        } else {
            dao.deleteBookmark(pageId)
        }
    }

    private suspend fun updateTopicAffinity(language: String, topicKey: String, delta: Double) {
        val clamp = rankingConfig.personalization.topicClamp
        val boundedDelta = delta.coerceIn(
            -rankingConfig.personalization.dailyDriftCap,
            rankingConfig.personalization.dailyDriftCap,
        )
        val existing = dao.topicAffinity(language, topicKey)
        val newScore = ((existing?.score ?: 0.0) + boundedDelta).coerceIn(clamp.min, clamp.max)
        dao.upsertTopicAffinity(
            TopicAffinityEntity(
                lang = language,
                topicKey = topicKey,
                score = newScore,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}

private fun SaveFolderSummaryRow.toDomain(): SaveFolderSummary {
    return SaveFolderSummary(
        folderId = folderId,
        name = name,
        isDefault = isDefault,
        articleCount = articleCount,
    )
}

private fun ArticleWithBookmark.toDomain(): ArticleCard {
    val normalizedTopic = TopicClassifier.normalizeTopic(
        rawTopic = topicKey,
        title = title,
        summary = summary,
    )
    return ArticleCard(
        pageId = pageId,
        lang = lang,
        title = title,
        normalizedTitle = normalizedTitle,
        summary = summary,
        wikiUrl = wikiUrl,
        topicKey = normalizedTopic,
        qualityScore = qualityScore,
        isDisambiguation = isDisambiguation,
        sourceRevId = sourceRevId,
        updatedAt = updatedAt,
        bookmarked = bookmarked,
    )
}
