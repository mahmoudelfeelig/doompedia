package com.feelbachelor.doompedia.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WikiDao {
    @Query("SELECT COUNT(*) FROM articles")
    suspend fun articleCount(): Int

    @Upsert
    suspend fun upsertArticles(rows: List<ArticleEntity>)

    @Query("DELETE FROM aliases WHERE pageId IN (:pageIds)")
    suspend fun deleteAliasesForPages(pageIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(rows: List<AliasEntity>)

    @Query("DELETE FROM articles WHERE pageId IN (:pageIds)")
    suspend fun deleteArticles(pageIds: List<Long>)

    @Query(
        """
        SELECT a.pageId, a.lang, a.title, a.normalizedTitle, a.summary, a.wikiUrl,
               a.topicKey, a.qualityScore, a.isDisambiguation, a.sourceRevId, a.updatedAt,
               CASE WHEN b.pageId IS NULL THEN 0 ELSE 1 END AS bookmarked
        FROM articles a
        LEFT JOIN bookmarks b ON b.pageId = a.pageId
        WHERE a.lang = :lang AND a.isDisambiguation = 0
        ORDER BY a.qualityScore DESC, a.pageId ASC
        LIMIT :limit
        """
    )
    suspend fun feedCandidates(lang: String, limit: Int): List<ArticleWithBookmark>

    @Query(
        """
        SELECT a.pageId, a.lang, a.title, a.normalizedTitle, a.summary, a.wikiUrl,
               a.topicKey, a.qualityScore, a.isDisambiguation, a.sourceRevId, a.updatedAt,
               CASE WHEN b.pageId IS NULL THEN 0 ELSE 1 END AS bookmarked
        FROM articles a
        LEFT JOIN bookmarks b ON b.pageId = a.pageId
        WHERE a.lang = :lang AND a.normalizedTitle = :normalizedQuery
        LIMIT :limit
        """
    )
    suspend fun searchExactTitle(lang: String, normalizedQuery: String, limit: Int): List<ArticleWithBookmark>

    @Query(
        """
        SELECT a.pageId, a.lang, a.title, a.normalizedTitle, a.summary, a.wikiUrl,
               a.topicKey, a.qualityScore, a.isDisambiguation, a.sourceRevId, a.updatedAt,
               CASE WHEN b.pageId IS NULL THEN 0 ELSE 1 END AS bookmarked
        FROM articles a
        LEFT JOIN bookmarks b ON b.pageId = a.pageId
        WHERE a.lang = :lang AND a.normalizedTitle LIKE :normalizedPrefix || '%'
        ORDER BY a.normalizedTitle
        LIMIT :limit
        """
    )
    suspend fun searchTitlePrefix(lang: String, normalizedPrefix: String, limit: Int): List<ArticleWithBookmark>

    @Query(
        """
        SELECT a.pageId, a.lang, a.title, a.normalizedTitle, a.summary, a.wikiUrl,
               a.topicKey, a.qualityScore, a.isDisambiguation, a.sourceRevId, a.updatedAt,
               CASE WHEN b.pageId IS NULL THEN 0 ELSE 1 END AS bookmarked
        FROM aliases alias
        JOIN articles a ON a.pageId = alias.pageId
        LEFT JOIN bookmarks b ON b.pageId = a.pageId
        WHERE alias.lang = :lang
          AND (alias.normalizedAlias = :normalizedQuery OR alias.normalizedAlias LIKE :normalizedPrefix || '%')
        ORDER BY alias.normalizedAlias
        LIMIT :limit
        """
    )
    suspend fun searchAlias(lang: String, normalizedQuery: String, normalizedPrefix: String, limit: Int): List<ArticleWithBookmark>

    @Query(
        """
        SELECT a.pageId, a.lang, a.title, a.normalizedTitle, a.summary, a.wikiUrl,
               a.topicKey, a.qualityScore, a.isDisambiguation, a.sourceRevId, a.updatedAt,
               CASE WHEN b.pageId IS NULL THEN 0 ELSE 1 END AS bookmarked
        FROM articles a
        LEFT JOIN bookmarks b ON b.pageId = a.pageId
        WHERE a.lang = :lang
          AND substr(a.normalizedTitle, 1, 1) = :firstChar
          AND length(a.normalizedTitle) BETWEEN :minLen AND :maxLen
        ORDER BY a.qualityScore DESC
        LIMIT :limit
        """
    )
    suspend fun typoCandidates(
        lang: String,
        firstChar: String,
        minLen: Int,
        maxLen: Int,
        limit: Int,
    ): List<ArticleWithBookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(row: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE pageId = :pageId")
    suspend fun deleteBookmark(pageId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE pageId = :pageId)")
    suspend fun isBookmarked(pageId: Long): Boolean

    @Insert
    suspend fun insertHistory(row: HistoryEntity)

    @Query("SELECT topicKey FROM history ORDER BY openedAt DESC LIMIT :limit")
    suspend fun recentTopics(limit: Int): List<String>

    @Query("SELECT * FROM topic_affinity WHERE lang = :lang")
    suspend fun topicAffinities(lang: String): List<TopicAffinityEntity>

    @Query("SELECT * FROM topic_affinity WHERE lang = :lang AND topicKey = :topicKey LIMIT 1")
    suspend fun topicAffinity(lang: String, topicKey: String): TopicAffinityEntity?

    @Upsert
    suspend fun upsertTopicAffinity(row: TopicAffinityEntity)
}
