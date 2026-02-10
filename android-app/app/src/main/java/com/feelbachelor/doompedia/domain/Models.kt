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

data class SaveFolderSummary(
    val folderId: Long,
    val name: String,
    val isDefault: Boolean,
    val articleCount: Int,
)

enum class ReadSort {
    NEWEST_FIRST,
    OLDEST_FIRST,
}

enum class PersonalizationLevel {
    OFF,
    LOW,
    MEDIUM,
    HIGH,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class FeedMode {
    OFFLINE,
    ONLINE,
}
