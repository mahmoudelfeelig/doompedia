package com.feelbachelor.doompedia.data.importer

import kotlinx.serialization.Serializable

@Serializable
data class PackManifest(
    val packId: String,
    val language: String,
    val version: Int,
    val createdAt: String,
    val recordCount: Int,
    val description: String? = null,
    val packTags: List<String> = emptyList(),
    val compression: String,
    val shards: List<PackShard>,
    val delta: PackDelta? = null,
    val topicDistribution: Map<String, Int> = emptyMap(),
    val entityDistribution: Map<String, Int> = emptyMap(),
    val sampleKeywords: List<String> = emptyList(),
    val attribution: PackAttribution,
)

@Serializable
data class PackShard(
    val id: String,
    val url: String,
    val sha256: String,
    val records: Int,
    val bytes: Long,
)

@Serializable
data class PackDelta(
    val baseVersion: Int,
    val targetVersion: Int,
    val url: String,
    val sha256: String,
    val ops: Int,
)

@Serializable
data class PackAttribution(
    val source: String,
    val license: String,
    val licenseUrl: String,
    val requiredNotice: String,
)

@Serializable
data class ShardRow(
    val article: ShardArticle,
    val aliases: List<String> = emptyList(),
)

@Serializable
data class ShardArticle(
    val page_id: Long,
    val lang: String,
    val title: String,
    val normalized_title: String,
    val summary: String,
    val wiki_url: String,
    val topic_key: String,
    val quality_score: Double,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val is_disambiguation: Boolean = false,
    val source_rev_id: Long? = null,
    val updated_at: String,
)

@Serializable
data class DeltaRow(
    val op: String,
    val record: ShardArticle? = null,
    val aliases: List<String> = emptyList(),
    val page_id: Long? = null,
)
