import Foundation

struct RankingConfig: Codable {
    let version: Int
    let weights: RankingWeights
    let personalization: PersonalizationConfig
    let guardrails: GuardrailsConfig
    let search: SearchConfig
}

struct RankingWeights: Codable {
    let interest: Double
    let novelty: Double
    let diversity: Double
    let quality: Double
    let repetitionPenalty: Double
}

struct PersonalizationConfig: Codable {
    let defaultLevel: String
    let levels: [String: Double]
    let learningRates: [String: Double]
    let dailyDriftCap: Double
    let topicClamp: TopicClamp
}

struct TopicClamp: Codable {
    let min: Double
    let max: Double
}

struct GuardrailsConfig: Codable {
    let explorationFloor: Double
    let windowSize: Int
    let maxSameTopicInWindow: Int
    let minDistinctTopicsInWindow: Int
    let cooldownCards: Int
}

struct SearchConfig: Codable {
    let typoDistance: Int
    let typoMinQueryLength: Int
    let maxTypoCandidates: Int
    let maxResults: Int
}

enum RankingConfigLoader {
    static func load() throws -> RankingConfig {
        guard let url = Bundle.main.url(forResource: "ranking-config.v1", withExtension: "json") else {
            throw NSError(domain: "Doompedia", code: 1, userInfo: [NSLocalizedDescriptionKey: "Missing ranking config asset"])
        }

        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(RankingConfig.self, from: data)
    }
}
