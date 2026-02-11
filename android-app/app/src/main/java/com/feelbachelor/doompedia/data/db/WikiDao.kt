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

    @Query("SELECT COUNT(*) FROM articles WHERE lang = :lang AND isDisambiguation = 0")
    suspend fun articleCount(lang: String): Int

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
        WHERE a.lang = :lang AND a.isDisambiguation = 0
        ORDER BY a.qualityScore DESC, a.pageId ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun feedCandidatesPage(lang: String, limit: Int, offset: Int): List<ArticleWithBookmark>

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
    suspend fun deleteBookmark(pageId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE pageId = :pageId)")
    suspend fun isBookmarked(pageId: Long): Boolean

    @Query(
        """
        INSERT OR IGNORE INTO save_folders(folderId, name, isDefault, createdAt)
        VALUES (1, 'Bookmarks', 1, :createdAt)
        """
    )
    suspend fun ensureDefaultFolder(createdAt: Long)

    @Query(
        """
        INSERT OR IGNORE INTO save_folders(folderId, name, isDefault, createdAt)
        VALUES (2, 'Read', 1, :createdAt)
        """
    )
    suspend fun ensureReadFolder(createdAt: Long)

    @Query(
        """
        INSERT OR IGNORE INTO article_folder_refs(folderId, pageId, createdAt)
        SELECT 1, b.pageId, b.createdAt
        FROM bookmarks b
        """
    )
    suspend fun backfillBookmarksIntoDefaultFolder()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(row: SaveFolderEntity): Long

    @Upsert
    suspend fun upsertFolder(row: SaveFolderEntity)

    @Query(
        """
        SELECT f.folderId AS folderId, f.name AS name, f.isDefault AS isDefault, COUNT(r.pageId) AS articleCount
        FROM save_folders f
        LEFT JOIN article_folder_refs r ON r.folderId = f.folderId
        WHERE f.folderId != 2
        GROUP BY f.folderId
        
        UNION ALL

        SELECT f.folderId AS folderId, f.name AS name, f.isDefault AS isDefault,
               (
                 SELECT COUNT(DISTINCT h.pageId)
                 FROM history h
                 JOIN articles a2 ON a2.pageId = h.pageId
                 WHERE a2.lang = :lang
               ) AS articleCount
        FROM save_folders f
        WHERE f.folderId = 2

        ORDER BY isDefault DESC, name ASC
        """
    )
    suspend fun saveFoldersWithCounts(lang: String): List<SaveFolderSummaryRow>

    @Query("DELETE FROM save_folders WHERE folderId = :folderId AND isDefault = 0")
    suspend fun deleteFolder(folderId: Long): Int

    @Query("DELETE FROM save_folders WHERE folderId = :folderId")
    suspend fun forceDeleteFolder(folderId: Long): Int

    @Query("SELECT folderId FROM article_folder_refs WHERE pageId = :pageId")
    suspend fun folderIdsForArticle(pageId: Long): List<Long>

    @Query("DELETE FROM article_folder_refs WHERE pageId = :pageId")
    suspend fun clearFolderRefsForArticle(pageId: Long)

    @Query("DELETE FROM article_folder_refs WHERE folderId = :folderId")
    suspend fun clearFolderRefsForFolder(folderId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolderRefs(rows: List<ArticleFolderRefEntity>)

    @Query("DELETE FROM article_folder_refs WHERE folderId = :folderId AND pageId = :pageId")
    suspend fun deleteFolderRef(folderId: Long, pageId: Long): Int

    @Query(
        """
        SELECT a.pageId, a.lang, a.title, a.normalizedTitle, a.summary, a.wikiUrl,
               a.topicKey, a.qualityScore, a.isDisambiguation, a.sourceRevId, a.updatedAt,
               CASE WHEN b.pageId IS NULL THEN 0 ELSE 1 END AS bookmarked
        FROM article_folder_refs r
        JOIN articles a ON a.pageId = r.pageId
        LEFT JOIN bookmarks b ON b.pageId = a.pageId
        WHERE r.folderId = :folderId
        ORDER BY r.createdAt DESC, a.pageId DESC
        LIMIT :limit
        """
    )
    suspend fun savedCardsInFolder(folderId: Long, limit: Int): List<ArticleWithBookmark>

    @Query(
        """
        SELECT a.pageId, a.lang, a.title, a.normalizedTitle, a.summary, a.wikiUrl,
               a.topicKey, a.qualityScore, a.isDisambiguation, a.sourceRevId, a.updatedAt,
               CASE WHEN b.pageId IS NULL THEN 0 ELSE 1 END AS bookmarked
        FROM articles a
        JOIN (
          SELECT pageId, MAX(openedAt) AS orderTs
          FROM history
          GROUP BY pageId
        ) h ON h.pageId = a.pageId
        LEFT JOIN bookmarks b ON b.pageId = a.pageId
        WHERE a.lang = :lang
        ORDER BY h.orderTs DESC, a.pageId DESC
        LIMIT :limit
        """
    )
    suspend fun readActivityLatest(lang: String, limit: Int): List<ArticleWithBookmark>

    @Query(
        """
        SELECT a.pageId, a.lang, a.title, a.normalizedTitle, a.summary, a.wikiUrl,
               a.topicKey, a.qualityScore, a.isDisambiguation, a.sourceRevId, a.updatedAt,
               CASE WHEN b.pageId IS NULL THEN 0 ELSE 1 END AS bookmarked
        FROM articles a
        JOIN (
          SELECT pageId, MIN(openedAt) AS orderTs
          FROM history
          GROUP BY pageId
        ) h ON h.pageId = a.pageId
        LEFT JOIN bookmarks b ON b.pageId = a.pageId
        WHERE a.lang = :lang
        ORDER BY h.orderTs ASC, a.pageId ASC
        LIMIT :limit
        """
    )
    suspend fun readActivityEarliest(lang: String, limit: Int): List<ArticleWithBookmark>

    @Insert
    suspend fun insertHistory(row: HistoryEntity)

    @Insert
    suspend fun insertHistory(rows: List<HistoryEntity>)

    @Query("SELECT topicKey FROM history ORDER BY openedAt DESC LIMIT :limit")
    suspend fun recentTopics(limit: Int): List<String>

    @Query("SELECT * FROM topic_affinity WHERE lang = :lang")
    suspend fun topicAffinities(lang: String): List<TopicAffinityEntity>

    @Query("SELECT * FROM topic_affinity WHERE lang = :lang AND topicKey = :topicKey LIMIT 1")
    suspend fun topicAffinity(lang: String, topicKey: String): TopicAffinityEntity?

    @Upsert
    suspend fun upsertTopicAffinity(row: TopicAffinityEntity)

    @Query("SELECT * FROM save_folders ORDER BY isDefault DESC, name ASC")
    suspend fun allFolders(): List<SaveFolderEntity>

    @Query("SELECT * FROM article_folder_refs WHERE folderId IN (:folderIds)")
    suspend fun folderRefsForFolders(folderIds: List<Long>): List<ArticleFolderRefEntity>

    @Query("SELECT pageId FROM articles WHERE pageId IN (:pageIds)")
    suspend fun existingArticleIds(pageIds: List<Long>): List<Long>

    @Query("SELECT pageId FROM history GROUP BY pageId ORDER BY MAX(openedAt) DESC")
    suspend fun readHistoryPageIds(): List<Long>

    @Query("SELECT folderId FROM save_folders WHERE name = :name LIMIT 1")
    suspend fun folderIdByName(name: String): Long?
}
