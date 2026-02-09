package com.feelbachelor.doompedia.data.repo

import com.feelbachelor.doompedia.data.db.ArticleWithBookmark
import com.feelbachelor.doompedia.data.db.BookmarkEntity
import com.feelbachelor.doompedia.data.db.HistoryEntity
import com.feelbachelor.doompedia.data.db.TopicAffinityEntity
import com.feelbachelor.doompedia.data.db.WikiDao
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.RankedCard
import com.feelbachelor.doompedia.domain.editDistanceAtMostOne
import com.feelbachelor.doompedia.domain.normalizeSearch
import com.feelbachelor.doompedia.ranking.FeedRanker
import com.feelbachelor.doompedia.ranking.RankingConfig

class WikiRepository(
    private val dao: WikiDao,
    private val rankingConfig: RankingConfig,
    private val ranker: FeedRanker,
) {
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
                language = language,
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

    suspend fun toggleBookmark(pageId: Long): Boolean {
        val bookmarked = dao.isBookmarked(pageId)
        if (bookmarked) {
            dao.deleteBookmark(pageId)
            return false
        }
        dao.upsertBookmark(
            BookmarkEntity(
                pageId = pageId,
                createdAt = System.currentTimeMillis(),
            )
        )
        return true
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

private fun ArticleWithBookmark.toDomain(): ArticleCard {
    return ArticleCard(
        pageId = pageId,
        lang = lang,
        title = title,
        normalizedTitle = normalizedTitle,
        summary = summary,
        wikiUrl = wikiUrl,
        topicKey = topicKey,
        qualityScore = qualityScore,
        isDisambiguation = isDisambiguation,
        sourceRevId = sourceRevId,
        updatedAt = updatedAt,
        bookmarked = bookmarked,
    )
}
