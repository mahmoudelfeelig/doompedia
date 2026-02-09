import Foundation

struct PackManifest: Codable {
    let packId: String
    let language: String
    let version: Int
    let createdAt: String
    let recordCount: Int
    let compression: String
    let shards: [PackShard]
    let delta: PackDelta?
    let topicDistribution: [String: Int]?
    let attribution: PackAttribution
}

struct PackShard: Codable {
    let id: String
    let url: String
    let sha256: String
    let records: Int
    let bytes: Int64
}

struct PackDelta: Codable {
    let baseVersion: Int
    let targetVersion: Int
    let url: String
    let sha256: String
    let ops: Int
}

struct PackAttribution: Codable {
    let source: String
    let license: String
    let licenseUrl: String
    let requiredNotice: String
}

struct ShardArticlePayload: Codable {
    let page_id: Int64
    let lang: String
    let title: String
    let normalized_title: String
    let summary: String
    let wiki_url: String
    let topic_key: String
    let quality_score: Double
    let is_disambiguation: Bool
    let source_rev_id: Int64?
    let updated_at: String
}

struct ShardRowPayload: Codable {
    let article: ShardArticlePayload
    let aliases: [String]
}

struct DeltaRowPayload: Codable {
    let op: String
    let record: ShardArticlePayload?
    let aliases: [String]?
    let page_id: Int64?
}
