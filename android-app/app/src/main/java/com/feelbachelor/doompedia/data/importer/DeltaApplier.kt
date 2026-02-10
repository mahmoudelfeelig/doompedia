package com.feelbachelor.doompedia.data.importer

import androidx.room.withTransaction
import com.feelbachelor.doompedia.data.db.AliasEntity
import com.feelbachelor.doompedia.data.db.ArticleEntity
import com.feelbachelor.doompedia.data.db.WikiDatabase
import com.feelbachelor.doompedia.domain.normalizeSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.GZIPInputStream

class DeltaApplier(
    private val db: WikiDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun apply(deltaFile: File): Int {
        return withContext(Dispatchers.IO) {
            val stream = when {
                deltaFile.name.endsWith(".gz") -> GZIPInputStream(deltaFile.inputStream())
                deltaFile.name.endsWith(".zst") -> error("zstd deltas require runtime decoder integration")
                else -> deltaFile.inputStream()
            }

            val upserts = mutableListOf<ArticleEntity>()
            val aliases = mutableListOf<AliasEntity>()
            val updatedPageIds = mutableListOf<Long>()
            val deletes = mutableListOf<Long>()
            var applied = 0

            stream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val row = json.decodeFromString(DeltaRow.serializer(), line)
                    when (row.op) {
                        "upsert" -> {
                            val record = requireNotNull(row.record)
                            upserts += ArticleEntity(
                                pageId = record.page_id,
                                lang = record.lang,
                                title = record.title,
                                normalizedTitle = record.normalized_title.ifBlank { normalizeSearch(record.title) },
                                summary = record.summary,
                                wikiUrl = record.wiki_url,
                                topicKey = TopicClassifier.normalizeTopic(
                                    rawTopic = record.topic_key,
                                    title = record.title,
                                    summary = record.summary,
                                ),
                                qualityScore = record.quality_score,
                                isDisambiguation = record.is_disambiguation,
                                sourceRevId = record.source_rev_id,
                                updatedAt = record.updated_at,
                            )
                            updatedPageIds += record.page_id
                            row.aliases.forEach { alias ->
                                aliases += AliasEntity(
                                    pageId = record.page_id,
                                    lang = record.lang,
                                    alias = alias,
                                    normalizedAlias = normalizeSearch(alias),
                                )
                            }
                            applied++
                        }

                        "delete" -> {
                            val pageId = requireNotNull(row.page_id)
                            deletes += pageId
                            applied++
                        }

                        else -> error("Unknown delta op '${row.op}'")
                    }

                    if (upserts.size + deletes.size >= 1_000) {
                        flush(upserts, aliases, updatedPageIds, deletes)
                    }
                }
            }

            flush(upserts, aliases, updatedPageIds, deletes)
            applied
        }
    }

    private suspend fun flush(
        upserts: MutableList<ArticleEntity>,
        aliases: MutableList<AliasEntity>,
        updatedPageIds: MutableList<Long>,
        deletes: MutableList<Long>,
    ) {
        if (upserts.isEmpty() && deletes.isEmpty()) return

        db.withTransaction {
            val dao = db.wikiDao()
            if (upserts.isNotEmpty()) {
                dao.upsertArticles(upserts)
                dao.deleteAliasesForPages(updatedPageIds)
                if (aliases.isNotEmpty()) {
                    dao.insertAliases(aliases)
                }
            }

            if (deletes.isNotEmpty()) {
                dao.deleteArticles(deletes)
            }
        }

        upserts.clear()
        aliases.clear()
        updatedPageIds.clear()
        deletes.clear()
    }
}
