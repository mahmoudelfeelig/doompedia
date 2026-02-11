import Foundation

enum CardKeywords {
    private static let stableTopics: Set<String> = [
        "science",
        "technology",
        "history",
        "geography",
        "culture",
        "politics",
        "economics",
        "sports",
        "health",
        "environment",
        "society",
        "biography",
        "general",
    ]

    private static let keywordBuckets: [(String, [String])] = [
        ("biography", ["born", "died", "actor", "author", "scientist", "politician", "player"]),
        ("science", ["physics", "chemistry", "biology", "mathematics", "astronomy", "scientific"]),
        ("technology", ["software", "computer", "internet", "digital", "algorithm", "device"]),
        ("history", ["war", "empire", "century", "historical", "revolution", "ancient", "dynasty"]),
        ("geography", ["city", "country", "region", "river", "mountain", "capital", "province"]),
        ("politics", ["government", "election", "parliament", "policy", "minister", "president"]),
        ("culture", ["music", "film", "literature", "art", "religion", "language"]),
        ("economics", ["economy", "finance", "market", "trade", "industry", "currency"]),
        ("health", ["medicine", "disease", "medical", "health", "hospital", "symptom"]),
        ("sports", ["football", "basketball", "olympic", "athlete", "league", "championship"]),
        ("environment", ["climate", "ecology", "forest", "wildlife", "pollution", "conservation"]),
    ]

    private static let stopwords: Set<String> = [
        "about", "after", "before", "their", "there", "which", "while", "where", "these", "those",
        "through", "using", "under", "between", "during", "known", "wikipedia", "article", "entry",
        "first", "second", "third", "world", "state", "city", "country",
    ]

    static func preferenceKeys(
        title: String,
        summary: String,
        topicKey: String,
        maxKeys: Int = 12
    ) -> [String] {
        let text = "\(title) \(summary)".lowercased()
        var ordered: [String] = []
        var seen = Set<String>()

        func append(_ value: String) {
            let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !cleaned.isEmpty, !seen.contains(cleaned) else { return }
            seen.insert(cleaned)
            ordered.append(cleaned)
        }

        let canonicalPrimary = canonicalTopic(topicKey)
        if !canonicalPrimary.isEmpty, canonicalPrimary != "general" {
            append(canonicalPrimary)
        }

        for (topic, keywords) in keywordBuckets where keywords.contains(where: { text.contains($0) }) {
            append(topic)
        }

        if title.range(of: "list of", options: .caseInsensitive) != nil { append("culture") }
        if title.range(of: "university", options: .caseInsensitive) != nil { append("society") }
        if title.range(of: "city", options: .caseInsensitive) != nil { append("geography") }
        if title.range(of: "war", options: .caseInsensitive) != nil { append("history") }

        if ordered.isEmpty {
            append("general")
        }

        for token in tokenize(text: text) {
            if token.count >= 4, !stopwords.contains(token) {
                append(token.replacingOccurrences(of: " ", with: "-"))
            }
            if ordered.count >= maxKeys { break }
        }

        return Array(ordered.prefix(maxKeys))
    }

    static func primaryTopic(title: String, summary: String, topicKey: String) -> String {
        let keys = preferenceKeys(title: title, summary: summary, topicKey: topicKey, maxKeys: 8)
        if let stable = keys.first(where: { stableTopics.contains($0) && $0 != "general" }) {
            return stable
        }
        let fallback = canonicalTopic(topicKey)
        return fallback.isEmpty ? "general" : fallback
    }

    static func displayTags(
        title: String,
        summary: String,
        topicKey: String,
        bookmarked: Bool,
        maxTags: Int = 6
    ) -> [String] {
        var ordered: [String] = []
        var seen = Set<String>()

        func append(_ value: String) {
            guard !value.isEmpty, !seen.contains(value) else { return }
            seen.insert(value)
            ordered.append(value)
        }

        for key in preferenceKeys(title: title, summary: summary, topicKey: topicKey, maxKeys: maxTags * 2) where stableTopics.contains(key) {
            append(prettyTopic(key))
        }

        if title.range(of: "list of", options: .caseInsensitive) != nil { append("Lists") }
        if title.range(of: "university", options: .caseInsensitive) != nil { append("Education") }
        if bookmarked { append("Saved") }

        return Array(ordered.prefix(maxTags))
    }

    static func prettyTopic(_ raw: String) -> String {
        let normalized = raw
            .replacingOccurrences(of: "-", with: " ")
            .replacingOccurrences(of: "_", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return "General" }
        return normalized
            .split(separator: " ")
            .map { token in
                let lower = token.lowercased()
                return lower.prefix(1).uppercased() + lower.dropFirst()
            }
            .joined(separator: " ")
    }

    private static func canonicalTopic(_ rawTopic: String) -> String {
        let canonical = rawTopic
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: "_", with: "-")
            .replacingOccurrences(of: " ", with: "-")
            .replacingOccurrences(of: "-+", with: "-", options: .regularExpression)

        switch canonical {
        case "history-of":
            return "history"
        case "geography-of":
            return "geography"
        case "economy-of":
            return "economics"
        case "list-of":
            return "culture"
        default:
            return canonical
        }
    }

    private static func tokenize(text: String) -> [String] {
        let pattern = "[A-Za-z][A-Za-z-]{2,}"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        return regex.matches(in: text, options: [], range: range).compactMap { match in
            guard let tokenRange = Range(match.range, in: text) else { return nil }
            return String(text[tokenRange]).lowercased()
        }
    }
}
