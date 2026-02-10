package com.feelbachelor.doompedia.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["lang", "normalizedTitle"]),
        Index(value = ["lang", "topicKey"]),
    ],
)
data class ArticleEntity(
    @PrimaryKey val pageId: Long,
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
)

@Entity(
    tableName = "aliases",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["pageId"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["lang", "normalizedAlias"]),
        Index(value = ["pageId"]),
    ],
)
data class AliasEntity(
    @PrimaryKey(autoGenerate = true) val aliasId: Long = 0,
    val pageId: Long,
    val lang: String,
    val alias: String,
    val normalizedAlias: String,
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val pageId: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "save_folders",
    indices = [
        Index(value = ["name"], unique = true),
    ],
)
data class SaveFolderEntity(
    @PrimaryKey(autoGenerate = true) val folderId: Long = 0,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "article_folder_refs",
    primaryKeys = ["folderId", "pageId"],
    foreignKeys = [
        ForeignKey(
            entity = SaveFolderEntity::class,
            parentColumns = ["folderId"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["pageId"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pageId"]),
        Index(value = ["folderId"]),
    ],
)
data class ArticleFolderRefEntity(
    val folderId: Long,
    val pageId: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "history",
    indices = [
        Index(value = ["openedAt"]),
        Index(value = ["topicKey"]),
    ],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val topicKey: String,
    val openedAt: Long,
)

@Entity(
    tableName = "topic_affinity",
    primaryKeys = ["lang", "topicKey"],
)
data class TopicAffinityEntity(
    val lang: String,
    val topicKey: String,
    val score: Double,
    val updatedAt: Long,
)

data class ArticleWithBookmark(
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

data class SaveFolderSummaryRow(
    val folderId: Long,
    val name: String,
    val isDefault: Boolean,
    val articleCount: Int,
)
