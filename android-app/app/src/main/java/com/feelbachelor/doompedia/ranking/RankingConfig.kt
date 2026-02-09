package com.feelbachelor.doompedia.ranking

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RankingConfig(
    val version: Int,
    val weights: RankingWeights,
    val personalization: PersonalizationConfig,
    val guardrails: GuardrailsConfig,
    val search: SearchConfig,
)

@Serializable
data class RankingWeights(
    val interest: Double,
    val novelty: Double,
    val diversity: Double,
    val quality: Double,
    @SerialName("repetitionPenalty")
    val repetitionPenalty: Double,
)

@Serializable
data class PersonalizationConfig(
    val defaultLevel: String,
    val levels: Map<String, Double>,
    val learningRates: Map<String, Double>,
    val dailyDriftCap: Double,
    val topicClamp: TopicClamp,
)

@Serializable
data class TopicClamp(
    val min: Double,
    val max: Double,
)

@Serializable
data class GuardrailsConfig(
    val explorationFloor: Double,
    val windowSize: Int,
    val maxSameTopicInWindow: Int,
    val minDistinctTopicsInWindow: Int,
    val cooldownCards: Int,
)

@Serializable
data class SearchConfig(
    val typoDistance: Int,
    val typoMinQueryLength: Int,
    val maxTypoCandidates: Int,
    val maxResults: Int,
)
