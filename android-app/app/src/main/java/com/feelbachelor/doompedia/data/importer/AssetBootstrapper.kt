package com.feelbachelor.doompedia.data.importer

import android.content.Context
import androidx.room.withTransaction
import com.feelbachelor.doompedia.data.db.AliasEntity
import com.feelbachelor.doompedia.data.db.ArticleEntity
import com.feelbachelor.doompedia.data.db.WikiDatabase
import com.feelbachelor.doompedia.domain.normalizeSearch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SeedRow(
    val page_id: Long,
    val lang: String,
    val title: String,
    val summary: String,
    val wiki_url: String,
    val topic_key: String,
    val quality_score: Double = 0.5,
    val is_disambiguation: Boolean = false,
    val source_rev_id: Long? = null,
    val updated_at: String,
    val aliases: List<String> = emptyList(),
)

class AssetBootstrapper(
    private val context: Context,
    private val db: WikiDatabase,
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun ensureSeedData() {
        val dao = db.wikiDao()
        if (dao.articleCount() > 0) return

        val rows = context.assets
            .open("content/seed_en_cards.json")
            .bufferedReader()
            .use { it.readText() }
            .let { json.decodeFromString<List<SeedRow>>(it) }

        val articles = rows.map { row ->
            ArticleEntity(
                pageId = row.page_id,
                lang = row.lang,
                title = row.title,
                normalizedTitle = normalizeSearch(row.title),
                summary = row.summary,
                wikiUrl = row.wiki_url,
                topicKey = row.topic_key,
                qualityScore = row.quality_score,
                isDisambiguation = row.is_disambiguation,
                sourceRevId = row.source_rev_id,
                updatedAt = row.updated_at,
            )
        }

        val aliases = rows.flatMap { row ->
            row.aliases.map { alias ->
                AliasEntity(
                    pageId = row.page_id,
                    lang = row.lang,
                    alias = alias,
                    normalizedAlias = normalizeSearch(alias),
                )
            }
        }

        db.withTransaction {
            dao.upsertArticles(articles)
            if (aliases.isNotEmpty()) {
                dao.insertAliases(aliases)
            }
        }
    }
}
