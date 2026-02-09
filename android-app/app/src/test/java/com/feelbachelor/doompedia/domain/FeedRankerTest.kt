package com.feelbachelor.doompedia.domain

import com.feelbachelor.doompedia.ranking.FeedRanker
import com.feelbachelor.doompedia.ranking.GuardrailsConfig
import com.feelbachelor.doompedia.ranking.PersonalizationConfig
import com.feelbachelor.doompedia.ranking.RankingConfig
import com.feelbachelor.doompedia.ranking.RankingWeights
import com.feelbachelor.doompedia.ranking.SearchConfig
import com.feelbachelor.doompedia.ranking.TopicClamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRankerTest {
    private val config = RankingConfig(
        version = 1,
        weights = RankingWeights(
            interest = 0.4,
            novelty = 0.2,
            diversity = 0.1,
            quality = 0.2,
            repetitionPenalty = 0.2,
        ),
        personalization = PersonalizationConfig(
            defaultLevel = "LOW",
            levels = mapOf("OFF" to 0.0, "LOW" to 0.5, "MEDIUM" to 1.0),
            learningRates = mapOf("open" to 0.4, "hide" to -0.5),
            dailyDriftCap = 0.2,
            topicClamp = TopicClamp(min = -1.0, max = 1.0),
        ),
        guardrails = GuardrailsConfig(
            explorationFloor = 0.25,
            windowSize = 12,
            maxSameTopicInWindow = 1,
            minDistinctTopicsInWindow = 2,
            cooldownCards = 3,
        ),
        search = SearchConfig(
            typoDistance = 1,
            typoMinQueryLength = 5,
            maxTypoCandidates = 200,
            maxResults = 30,
        )
    )

    @Test
    fun rank_respectsTopicCapInWindow() {
        val scienceA = sampleCard(1, "science")
        val scienceB = sampleCard(2, "science")
        val history = sampleCard(3, "history")

        val ranker = FeedRanker(config)
        val ranked = ranker.rank(
            candidates = listOf(scienceA, scienceB, history),
            topicAffinity = mapOf("science" to 1.0, "history" to 0.3),
            recentlySeenTopics = emptyList(),
            level = PersonalizationLevel.MEDIUM,
            limit = 3,
        )

        val scienceCount = ranked.count { it.card.topicKey == "science" }
        assertEquals(1, scienceCount)
        assertTrue(ranked.isNotEmpty())
    }

    @Test
    fun rank_enforcesDistinctTopicGuardrailWhenWindowWouldCollapse() {
        val distinctConfig = config.copy(
            guardrails = config.guardrails.copy(
                maxSameTopicInWindow = 4,
                minDistinctTopicsInWindow = 3,
                windowSize = 4,
            ),
        )
        val ranker = FeedRanker(distinctConfig)
        val ranked = ranker.rank(
            candidates = listOf(
                sampleCard(1, "science"),
                sampleCard(2, "science"),
                sampleCard(3, "science"),
                sampleCard(4, "history"),
                sampleCard(5, "art"),
            ),
            topicAffinity = mapOf("science" to 1.0, "history" to 0.2, "art" to 0.1),
            recentlySeenTopics = emptyList(),
            level = PersonalizationLevel.MEDIUM,
            limit = 4,
        )

        val firstWindowTopics = ranked.take(4).map { it.card.topicKey }.toSet()
        assertTrue(firstWindowTopics.size >= 3)
    }

    private fun sampleCard(pageId: Long, topic: String): ArticleCard {
        return ArticleCard(
            pageId = pageId,
            lang = "en",
            title = "Card $pageId",
            normalizedTitle = "card $pageId",
            summary = "This is a sufficiently long summary for test card $pageId in topic $topic.",
            wikiUrl = "https://en.wikipedia.org/wiki/Card_$pageId",
            topicKey = topic,
            qualityScore = 0.8,
            isDisambiguation = false,
            sourceRevId = null,
            updatedAt = "2026-02-09T00:00:00Z",
            bookmarked = false,
        )
    }
}
