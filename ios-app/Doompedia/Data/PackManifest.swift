import Foundation

struct PackManifest: Codable {
    let packId: String
    let language: String
    let version: Int
    let createdAt: String
    let recordCount: Int
    let description: String?
    let packTags: [String]?
    let compression: String
    let shards: [PackShard]
    let delta: PackDelta?
    let topicDistribution: [String: Int]?
    let entityDistribution: [String: Int]?
    let sampleKeywords: [String]?
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

struct ShardArticlePayload: Decodable {
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

    private enum CodingKeys: String, CodingKey {
        case page_id
        case lang
        case title
        case normalized_title
        case summary
        case wiki_url
        case topic_key
        case quality_score
        case is_disambiguation
        case source_rev_id
        case updated_at
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        page_id = try container.decode(Int64.self, forKey: .page_id)
        lang = try container.decode(String.self, forKey: .lang)
        title = try container.decode(String.self, forKey: .title)
        normalized_title = try container.decode(String.self, forKey: .normalized_title)
        summary = try container.decode(String.self, forKey: .summary)
        wiki_url = try container.decode(String.self, forKey: .wiki_url)
        topic_key = try container.decode(String.self, forKey: .topic_key)
        quality_score = try container.decode(Double.self, forKey: .quality_score)
        is_disambiguation = try container.decodeFlexibleBool(forKey: .is_disambiguation)
        source_rev_id = try container.decodeIfPresent(Int64.self, forKey: .source_rev_id)
        updated_at = try container.decode(String.self, forKey: .updated_at)
    }
}

struct ShardRowPayload: Decodable {
    let article: ShardArticlePayload
    let aliases: [String]
}

struct DeltaRowPayload: Decodable {
    let op: String
    let record: ShardArticlePayload?
    let aliases: [String]?
    let page_id: Int64?
}

private extension KeyedDecodingContainer {
    func decodeFlexibleBool(forKey key: Key) throws -> Bool {
        if let boolValue = try decodeIfPresent(Bool.self, forKey: key) {
            return boolValue
        }

        if let intValue = try decodeIfPresent(Int.self, forKey: key) {
            switch intValue {
            case 0: return false
            case 1: return true
            default:
                throw DecodingError.dataCorruptedError(
                    forKey: key,
                    in: self,
                    debugDescription: "Expected 0/1 for boolean field, got \(intValue)"
                )
            }
        }

        if let textValue = try decodeIfPresent(String.self, forKey: key) {
            switch textValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
            case "false", "0", "no", "n": return false
            case "true", "1", "yes", "y": return true
            default:
                throw DecodingError.dataCorruptedError(
                    forKey: key,
                    in: self,
                    debugDescription: "Expected boolean-like string, got \(textValue)"
                )
            }
        }

        return false
    }
}
