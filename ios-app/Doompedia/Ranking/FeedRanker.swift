import Foundation

struct FeedRanker {
    let config: RankingConfig

    private struct BaseScore {
        let card: ArticleCard
        let interest: Double
        let novelty: Double
        let quality: Double
        let repetition: Double

        var score: Double {
            interest + novelty + quality - repetition
        }
    }

    func rank(
        candidates: [ArticleCard],
        topicAffinity: [String: Double],
        recentlySeenTopics: [String],
        level: PersonalizationLevel,
        limit: Int
    ) -> [RankedCard] {
        guard !candidates.isEmpty, limit > 0 else {
            return []
        }

        let levelFactor = config.personalization.levels[level.rawValue] ?? 0.0
        let topAffinityTopics = Set(
            topicAffinity
                .sorted(by: { $0.value > $1.value })
                .prefix(2)
                .map(\.key)
        )

        let orderedBase = candidates
            .map { card in
                let interest = (topicAffinity[card.topicKey] ?? 0.0) * config.weights.interest * levelFactor
                let novelty = noveltyScore(card: card, recent: recentlySeenTopics)
                let quality = card.qualityScore * config.weights.quality
                let repetition = repetitionPenalty(topic: card.topicKey, recent: recentlySeenTopics)
                return BaseScore(
                    card: card,
                    interest: interest,
                    novelty: novelty,
                    quality: quality,
                    repetition: repetition
                )
            }
            .sorted(by: { $0.score > $1.score })

        var result: [RankedCard] = []
        var selectedTopics: [String] = []
        var selectedIDs = Set<Int64>()
        let targetExplorationCount = max(1, Int(Double(limit) * config.guardrails.explorationFloor))
        var selectedExploration = 0

        for base in orderedBase {
            if result.count >= limit { break }

            let topic = base.card.topicKey
            let sameTopicCount = selectedTopics.filter { $0 == topic }.count
            if sameTopicCount >= config.guardrails.maxSameTopicInWindow { continue }
            if violatesDistinctTopicGuardrail(selectedTopics: selectedTopics, candidateTopic: topic) { continue }

            let isExploration = !topAffinityTopics.isEmpty && !topAffinityTopics.contains(topic)
            let slotsRemaining = limit - result.count
            let explorationNeeded = max(0, targetExplorationCount - selectedExploration)
            if !isExploration && explorationNeeded >= slotsRemaining {
                continue
            }

            let diversity = diversityScore(topic: topic, selected: selectedTopics)
            let why = buildWhy(
                topic: topic,
                interest: base.interest,
                novelty: base.novelty,
                diversity: diversity,
                isExploration: isExploration
            )
            let ranked = RankedCard(card: base.card, score: base.score + diversity, why: why)
            result.append(ranked)
            selectedTopics.append(topic)
            selectedIDs.insert(base.card.pageId)
            if isExploration {
                selectedExploration += 1
            }
        }

        if result.count < limit {
            for base in orderedBase where !selectedIDs.contains(base.card.pageId) {
                if result.count >= limit { break }
                let isExploration = !topAffinityTopics.isEmpty && !topAffinityTopics.contains(base.card.topicKey)
                let diversity = diversityScore(topic: base.card.topicKey, selected: selectedTopics)
                let why = buildWhy(
                    topic: base.card.topicKey,
                    interest: base.interest,
                    novelty: base.novelty,
                    diversity: diversity,
                    isExploration: isExploration
                )
                result.append(
                    RankedCard(
                        card: base.card,
                        score: base.score + diversity,
                        why: why
                    )
                )
                selectedTopics.append(base.card.topicKey)
                selectedIDs.insert(base.card.pageId)
            }
        }

        return Array(result.prefix(limit))
    }

    private func noveltyScore(card: ArticleCard, recent: [String]) -> Double {
        if recent.isEmpty { return config.weights.novelty }
        let inCooldown = recent.prefix(config.guardrails.cooldownCards).contains(card.topicKey)
        return inCooldown ? config.weights.novelty * 0.1 : config.weights.novelty
    }

    private func diversityScore(topic: String, selected: [String]) -> Double {
        if selected.isEmpty { return config.weights.diversity }
        let frequency = selected.filter { $0 == topic }.count
        return (1.0 / Double(1 + frequency)) * config.weights.diversity
    }

    private func repetitionPenalty(topic: String, recent: [String]) -> Double {
        let repeats = recent.filter { $0 == topic }.count
        return Double(repeats) * config.weights.repetitionPenalty * 0.25
    }

    private func violatesDistinctTopicGuardrail(selectedTopics: [String], candidateTopic: String) -> Bool {
        let windowSize = config.guardrails.windowSize
        let minDistinct = config.guardrails.minDistinctTopicsInWindow
        if windowSize <= 0 || minDistinct <= 0 { return false }
        if selectedTopics.count >= windowSize { return false }

        let currentDistinct = Set(selectedTopics)
        let nextDistinctCount = currentDistinct.contains(candidateTopic)
            ? currentDistinct.count
            : currentDistinct.count + 1
        let slotsRemainingAfterPick = windowSize - (selectedTopics.count + 1)
        let maxPossibleDistinct = nextDistinctCount + slotsRemainingAfterPick
        return maxPossibleDistinct < minDistinct
    }

    private func buildWhy(
        topic: String,
        interest: Double,
        novelty: Double,
        diversity: Double,
        isExploration: Bool
    ) -> String {
        var reasons: [String] = []
        if interest > 0.15 { reasons.append("matches your \(topic) interest") }
        if novelty > 0 { reasons.append("adds novelty") }
        if diversity > 0 { reasons.append("improves topic diversity") }
        if isExploration { reasons.append("keeps exploration healthy") }
        if reasons.isEmpty { reasons.append("is high quality") }
        return "Recommended because it " + reasons.joined(separator: ", ")
    }
}
