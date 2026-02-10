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
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

class PackInstaller(
    private val db: WikiDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun installFromDirectory(directory: File, expectedPackId: String? = null): PackManifest {
        return withContext(Dispatchers.IO) {
            val manifestFile = File(directory, "manifest.json")
            require(manifestFile.exists()) { "manifest.json not found in ${directory.path}" }
            val manifest = json.decodeFromString(PackManifest.serializer(), manifestFile.readText())
            if (expectedPackId != null) {
                require(manifest.packId == expectedPackId) {
                    "Manifest packId ${manifest.packId} does not match expected $expectedPackId"
                }
            }

            manifest.shards.forEach { shard ->
                val shardFile = resolveShardPath(directory, shard.url)
                require(shardFile.exists()) { "Missing shard file: ${shardFile.path}" }
                val digest = shardFile.sha256()
                require(digest.equals(shard.sha256, ignoreCase = true)) {
                    "Checksum mismatch for shard ${shard.id}"
                }
                applyShard(shardFile)
            }

            manifest
        }
    }

    private suspend fun applyShard(shardFile: File) {
        val inputStream = when {
            shardFile.name.endsWith(".gz") -> GZIPInputStream(shardFile.inputStream())
            shardFile.name.endsWith(".zst") -> error("zstd shards require runtime decoder integration")
            else -> shardFile.inputStream()
        }

        val articleBuffer = mutableListOf<ArticleEntity>()
        val aliasBuffer = mutableListOf<AliasEntity>()
        val pageIds = mutableListOf<Long>()

        inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                val row = json.decodeFromString(ShardRow.serializer(), line)
                val article = row.article
                articleBuffer += ArticleEntity(
                    pageId = article.page_id,
                    lang = article.lang,
                    title = article.title,
                    normalizedTitle = article.normalized_title.ifBlank { normalizeSearch(article.title) },
                    summary = article.summary,
                    wikiUrl = article.wiki_url,
                    topicKey = TopicClassifier.normalizeTopic(
                        rawTopic = article.topic_key,
                        title = article.title,
                        summary = article.summary,
                    ),
                    qualityScore = article.quality_score,
                    isDisambiguation = article.is_disambiguation,
                    sourceRevId = article.source_rev_id,
                    updatedAt = article.updated_at,
                )
                pageIds += article.page_id
                row.aliases.forEach { alias ->
                    aliasBuffer += AliasEntity(
                        pageId = article.page_id,
                        lang = article.lang,
                        alias = alias,
                        normalizedAlias = normalizeSearch(alias),
                    )
                }

                if (articleBuffer.size >= 1_000) {
                    flushBatch(articleBuffer, aliasBuffer, pageIds)
                }
            }
        }

        flushBatch(articleBuffer, aliasBuffer, pageIds)
    }

    private suspend fun flushBatch(
        articleBuffer: MutableList<ArticleEntity>,
        aliasBuffer: MutableList<AliasEntity>,
        pageIds: MutableList<Long>,
    ) {
        if (articleBuffer.isEmpty()) return
        db.withTransaction {
            val dao = db.wikiDao()
            dao.upsertArticles(articleBuffer)
            dao.deleteAliasesForPages(pageIds)
            if (aliasBuffer.isNotEmpty()) {
                dao.insertAliases(aliasBuffer)
            }
        }
        articleBuffer.clear()
        aliasBuffer.clear()
        pageIds.clear()
    }

    private fun resolveShardPath(baseDirectory: File, manifestPath: String): File {
        val candidate = File(baseDirectory, manifestPath)
        if (candidate.exists()) return candidate

        val trimmed = manifestPath.substringAfterLast('/')
        val fallback = File(baseDirectory, trimmed)
        if (fallback.exists()) return fallback

        return candidate
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { "%02x".format(it) }
}
