import Foundation

struct FeedRanker {
    let config: RankingConfig

    private struct TopicDescriptor {
        let primaryTopic: String
        let preferenceKeys: [String]
    }

    private struct BaseScore {
        let card: ArticleCard
        let primaryTopic: String
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
                let descriptor = describeTopics(card)
                let interest = interestScore(
                    topicAffinity: topicAffinity,
                    descriptor: descriptor,
                    levelFactor: levelFactor
                )
                let novelty = noveltyScore(primaryTopic: descriptor.primaryTopic, recent: recentlySeenTopics)
                let quality = card.qualityScore * config.weights.quality
                let repetition = repetitionPenalty(topic: descriptor.primaryTopic, recent: recentlySeenTopics)
                return BaseScore(
                    card: card,
                    primaryTopic: descriptor.primaryTopic,
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

            let topic = base.primaryTopic
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
                if selectedTopics.filter({ $0 == base.primaryTopic }).count >= config.guardrails.maxSameTopicInWindow { continue }
                if violatesDistinctTopicGuardrail(selectedTopics: selectedTopics, candidateTopic: base.primaryTopic) { continue }

                let isExploration = !topAffinityTopics.isEmpty && !topAffinityTopics.contains(base.primaryTopic)
                let diversity = diversityScore(topic: base.primaryTopic, selected: selectedTopics)
                let why = buildWhy(
                    topic: base.primaryTopic,
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
                selectedTopics.append(base.primaryTopic)
                selectedIDs.insert(base.card.pageId)
            }
        }

        return Array(result.prefix(limit))
    }

    private func describeTopics(_ card: ArticleCard) -> TopicDescriptor {
        let extracted = CardKeywords.preferenceKeys(
            title: card.title,
            summary: card.summary,
            topicKey: card.topicKey,
            maxKeys: 10
        )
        let explicitTopic = normalizeTopicKey(card.topicKey)
        let hasExplicitTopic = !explicitTopic.isEmpty && !["general", "unknown", "other"].contains(explicitTopic)
        var keys = extracted
        if hasExplicitTopic {
            keys = [explicitTopic] + extracted
        }
        keys = Array(NSOrderedSet(array: keys)) as? [String] ?? keys
        keys = Array(keys.prefix(10))
        let inferredPrimary = CardKeywords.primaryTopic(
            title: card.title,
            summary: card.summary,
            topicKey: card.topicKey
        )
        let primary = hasExplicitTopic ? explicitTopic : inferredPrimary
        return TopicDescriptor(primaryTopic: primary, preferenceKeys: keys)
    }

    private func normalizeTopicKey(_ raw: String) -> String {
        raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: "_", with: "-")
            .replacingOccurrences(of: "\\s+", with: "-", options: .regularExpression)
    }

    private func interestScore(
        topicAffinity: [String: Double],
        descriptor: TopicDescriptor,
        levelFactor: Double
    ) -> Double {
        let values = descriptor.preferenceKeys.map { topicAffinity[$0] ?? 0.0 }
        guard !values.isEmpty else { return 0.0 }

        let maxSignal = values.max() ?? 0.0
        let avgSignal = values.reduce(0.0, +) / Double(values.count)
        let blended = (maxSignal * 0.7) + (avgSignal * 0.3)
        return blended * config.weights.interest * levelFactor
    }

    private func noveltyScore(primaryTopic: String, recent: [String]) -> Double {
        if recent.isEmpty { return config.weights.novelty }
        let inCooldown = recent.prefix(config.guardrails.cooldownCards).contains(primaryTopic)
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
        if interest > 0.15 { reasons.append("you've shown interest in \(topic.replacingOccurrences(of: "-", with: " ")) topics") }
        if novelty > 0 { reasons.append("it adds novelty to avoid repetition") }
        if diversity > 0 { reasons.append("it improves topic diversity in your feed") }
        if isExploration { reasons.append("it keeps a healthy exploration ratio") }
        if reasons.isEmpty { reasons.append("it is a strong quality candidate") }
        return "Shown because " + reasons.joined(separator: "; ") + "."
    }
}
