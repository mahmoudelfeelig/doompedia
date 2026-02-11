package com.feelbachelor.doompedia.ranking

import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.CardKeywords
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.RankedCard
import kotlin.math.max

class FeedRanker(
    private val config: RankingConfig,
) {
    private data class TopicDescriptor(
        val primaryTopic: String,
        val preferenceKeys: List<String>,
    )

    private data class BaseScore(
        val card: ArticleCard,
        val primaryTopic: String,
        val interest: Double,
        val novelty: Double,
        val quality: Double,
        val repetition: Double,
    ) {
        val score: Double
            get() = interest + novelty + quality - repetition
    }

    fun rank(
        candidates: List<ArticleCard>,
        topicAffinity: Map<String, Double>,
        recentlySeenTopics: List<String>,
        level: PersonalizationLevel,
        limit: Int,
    ): List<RankedCard> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()

        val levelFactor = config.personalization.levels[level.name] ?: 0.0
        val topAffinityTopics = topicAffinity
            .entries
            .sortedByDescending { it.value }
            .take(2)
            .map { it.key }
            .toSet()

        val rankedBase = candidates
            .map { card ->
                val descriptor = describeTopics(card)
                val interest = interestScore(
                    topicAffinity = topicAffinity,
                    descriptor = descriptor,
                    levelFactor = levelFactor,
                )
                val novelty = noveltyScore(descriptor.primaryTopic, recentlySeenTopics)
                val quality = card.qualityScore * config.weights.quality
                val repetition = repetitionPenalty(descriptor.primaryTopic, recentlySeenTopics)
                BaseScore(
                    card = card,
                    primaryTopic = descriptor.primaryTopic,
                    interest = interest,
                    novelty = novelty,
                    quality = quality,
                    repetition = repetition,
                )
            }
            .sortedByDescending { it.score }

        val result = mutableListOf<RankedCard>()
        val selectedIds = mutableSetOf<Long>()
        val selectedTopics = mutableListOf<String>()
        val targetExplorationCount = max(1, (limit * config.guardrails.explorationFloor).toInt())
        var selectedExploration = 0

        for (base in rankedBase) {
            if (result.size >= limit) break

            val topic = base.primaryTopic
            if (selectedTopics.count { it == topic } >= config.guardrails.maxSameTopicInWindow) continue
            if (wouldViolateDistinctTopicGuardrail(selectedTopics, topic)) continue

            val isExploration = topAffinityTopics.isNotEmpty() && topic !in topAffinityTopics
            val slotsRemaining = limit - result.size
            val explorationNeeded = (targetExplorationCount - selectedExploration).coerceAtLeast(0)
            if (!isExploration && explorationNeeded >= slotsRemaining) continue

            val diversity = diversityScore(topic, selectedTopics)
            val score = base.score + diversity
            val why = buildWhy(
                topic = topic,
                interest = base.interest,
                novelty = base.novelty,
                diversity = diversity,
                isExploration = isExploration,
            )
            result += RankedCard(card = base.card, score = score, why = why)
            selectedIds += base.card.pageId
            selectedTopics += topic
            if (isExploration) selectedExploration++
        }

        if (result.size < limit) {
            for (base in rankedBase) {
                if (result.size >= limit) break
                if (!selectedIds.add(base.card.pageId)) continue
                if (selectedTopics.count { it == base.primaryTopic } >= config.guardrails.maxSameTopicInWindow) continue
                if (wouldViolateDistinctTopicGuardrail(selectedTopics, base.primaryTopic)) continue

                val diversity = diversityScore(base.primaryTopic, selectedTopics)
                val score = base.score + diversity
                val isExploration = topAffinityTopics.isNotEmpty() && base.primaryTopic !in topAffinityTopics
                val why = buildWhy(
                    topic = base.primaryTopic,
                    interest = base.interest,
                    novelty = base.novelty,
                    diversity = diversity,
                    isExploration = isExploration,
                )
                result += RankedCard(card = base.card, score = score, why = why)
                selectedTopics += base.primaryTopic
            }
        }

        return result.take(limit)
    }

    private fun describeTopics(card: ArticleCard): TopicDescriptor {
        val extracted = CardKeywords.preferenceKeys(
            title = card.title,
            summary = card.summary,
            topicKey = card.topicKey,
            maxKeys = 10,
        )
        val explicitTopic = normalizeTopicKey(card.topicKey)
        val hasExplicitTopic = explicitTopic.isNotBlank() && explicitTopic !in setOf("general", "unknown", "other")
        val keys = buildList {
            if (hasExplicitTopic) add(explicitTopic)
            addAll(extracted)
        }.distinct().take(10)
        val inferredPrimary = CardKeywords.primaryTopic(
            title = card.title,
            summary = card.summary,
            topicKey = card.topicKey,
        )
        val primary = if (hasExplicitTopic) explicitTopic else inferredPrimary
        return TopicDescriptor(primaryTopic = primary, preferenceKeys = keys)
    }

    private fun normalizeTopicKey(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace('_', '-')
            .replace(Regex("\\s+"), "-")
    }

    private fun interestScore(
        topicAffinity: Map<String, Double>,
        descriptor: TopicDescriptor,
        levelFactor: Double,
    ): Double {
        val values = descriptor.preferenceKeys.map { key -> topicAffinity[key] ?: 0.0 }
        if (values.isEmpty()) return 0.0
        val maxSignal = values.maxOrNull() ?: 0.0
        val avgSignal = values.average()
        val blended = (maxSignal * 0.7) + (avgSignal * 0.3)
        return blended * config.weights.interest * levelFactor
    }

    private fun noveltyScore(primaryTopic: String, recentTopics: List<String>): Double {
        if (recentTopics.isEmpty()) return config.weights.novelty
        return if (primaryTopic !in recentTopics.take(config.guardrails.cooldownCards)) {
            config.weights.novelty
        } else {
            config.weights.novelty * 0.1
        }
    }

    private fun diversityScore(topic: String, chosenTopics: List<String>): Double {
        if (chosenTopics.isEmpty()) return config.weights.diversity
        val frequency = chosenTopics.count { it == topic }
        return (1.0 / (1 + frequency)) * config.weights.diversity
    }

    private fun repetitionPenalty(topic: String, recentTopics: List<String>): Double {
        val repeats = recentTopics.count { it == topic }
        return repeats * config.weights.repetitionPenalty * 0.25
    }

    private fun wouldViolateDistinctTopicGuardrail(selectedTopics: List<String>, candidateTopic: String): Boolean {
        val windowSize = config.guardrails.windowSize
        val minDistinct = config.guardrails.minDistinctTopicsInWindow
        if (windowSize <= 0 || minDistinct <= 0) return false
        if (selectedTopics.size >= windowSize) return false

        val currentDistinct = selectedTopics.toSet()
        val nextDistinctCount = if (candidateTopic in currentDistinct) currentDistinct.size else currentDistinct.size + 1
        val slotsRemainingAfterPick = windowSize - (selectedTopics.size + 1)
        val maxPossibleDistinct = nextDistinctCount + slotsRemainingAfterPick
        return maxPossibleDistinct < minDistinct
    }

    private fun buildWhy(
        topic: String,
        interest: Double,
        novelty: Double,
        diversity: Double,
        isExploration: Boolean,
    ): String {
        val reasons = mutableListOf<String>()
        if (interest > 0.15) reasons += "you've shown interest in ${topic.replace('-', ' ')} topics"
        if (novelty > 0.0) reasons += "it adds novelty to avoid repetition"
        if (diversity > 0.0) reasons += "it improves topic diversity in your feed"
        if (isExploration) reasons += "it keeps a healthy exploration ratio"
        if (reasons.isEmpty()) reasons += "it is a strong quality candidate"
        return "Shown because " + reasons.joinToString(separator = "; ") + "."
    }
}
