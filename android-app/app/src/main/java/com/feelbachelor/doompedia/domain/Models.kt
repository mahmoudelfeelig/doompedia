package com.feelbachelor.doompedia.domain

data class ArticleCard(
    val pageId: Long,
    val lang: String,
    val title: String,
    val normalizedTitle: String,
    val summary: String,
    val wikiUrl: String,
    val topicKey: String,
    val qualityScore: Double,
    val isDisambiguation: Boolean,
    val sourceRevId: Long?,
    val updatedAt: String,
    val bookmarked: Boolean,
)

data class RankedCard(
    val card: ArticleCard,
    val score: Double,
    val why: String,
)

enum class PersonalizationLevel {
    OFF,
    LOW,
    MEDIUM,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
