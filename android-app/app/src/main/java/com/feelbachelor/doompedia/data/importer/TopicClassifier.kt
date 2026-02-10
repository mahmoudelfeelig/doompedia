package com.feelbachelor.doompedia.data.importer

object TopicClassifier {
    private val stableTopics = setOf(
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
    )

    private val keywordBuckets: List<Pair<String, List<String>>> = listOf(
        "biography" to listOf("born", "died", "actor", "author", "scientist", "politician", "player"),
        "history" to listOf("empire", "war", "century", "kingdom", "revolution", "ancient", "historical"),
        "science" to listOf("physics", "chemistry", "biology", "mathematics", "astronomy", "scientific"),
        "technology" to listOf("software", "computer", "internet", "digital", "algorithm", "device"),
        "geography" to listOf("river", "mountain", "city", "country", "region", "province", "capital"),
        "politics" to listOf("election", "government", "parliament", "minister", "policy", "party"),
        "economics" to listOf("economy", "market", "trade", "finance", "currency", "industry"),
        "health" to listOf("disease", "medical", "medicine", "health", "hospital", "symptom"),
        "sports" to listOf("football", "basketball", "olympic", "league", "athlete", "championship"),
        "environment" to listOf("climate", "ecology", "forest", "wildlife", "pollution", "conservation"),
        "culture" to listOf("music", "film", "literature", "art", "religion", "language"),
    )

    fun normalizeTopic(rawTopic: String, title: String, summary: String): String {
        val canonicalRaw = canonicalize(rawTopic)
        if (canonicalRaw in stableTopics && canonicalRaw != "general") return canonicalRaw
        if (canonicalRaw == "list-of") return "culture"
        if (canonicalRaw == "history-of") return "history"
        if (canonicalRaw == "geography-of") return "geography"
        if (canonicalRaw == "economy-of") return "economics"
        return inferFromText(title = title, summary = summary)
    }

    private fun inferFromText(title: String, summary: String): String {
        val text = (title + " " + summary).lowercase()
        for ((topic, keywords) in keywordBuckets) {
            if (keywords.any { keyword -> keyword in text }) {
                return topic
            }
        }
        return "general"
    }

    private fun canonicalize(rawTopic: String): String {
        return rawTopic
            .trim()
            .lowercase()
            .replace('_', '-')
            .replace(' ', '-')
            .replace(Regex("-+"), "-")
    }
}
