package com.feelbachelor.doompedia.domain

object CardKeywords {
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
        "science" to listOf("physics", "chemistry", "biology", "mathematics", "astronomy", "scientific"),
        "technology" to listOf("software", "computer", "internet", "digital", "algorithm", "device"),
        "history" to listOf("war", "empire", "century", "historical", "revolution", "ancient", "dynasty"),
        "geography" to listOf("city", "country", "region", "river", "mountain", "capital", "province"),
        "politics" to listOf("government", "election", "parliament", "policy", "minister", "president"),
        "culture" to listOf("music", "film", "literature", "art", "religion", "language"),
        "economics" to listOf("economy", "finance", "market", "trade", "industry", "currency"),
        "health" to listOf("medicine", "disease", "medical", "health", "hospital", "symptom"),
        "sports" to listOf("football", "basketball", "olympic", "athlete", "league", "championship"),
        "environment" to listOf("climate", "ecology", "forest", "wildlife", "pollution", "conservation"),
    )

    private val stopwords = setOf(
        "about", "after", "before", "their", "there", "which", "while", "where", "these", "those",
        "through", "using", "under", "between", "during", "known", "wikipedia", "article", "entry",
        "first", "second", "third", "world", "state", "city", "country",
    )

    private val tokenRegex = Regex("[A-Za-z][A-Za-z-]{2,}")

    fun preferenceKeys(
        title: String,
        summary: String,
        topicKey: String,
        maxKeys: Int = 12,
    ): List<String> {
        val text = "$title $summary".lowercase()
        val ordered = linkedSetOf<String>()

        val canonicalPrimary = canonicalTopic(topicKey)
        if (canonicalPrimary.isNotBlank() && canonicalPrimary != "general") {
            ordered += canonicalPrimary
        }

        keywordBuckets.forEach { (topic, keywords) ->
            if (keywords.any { keyword -> text.contains(keyword) }) {
                ordered += topic
            }
        }

        if (title.contains("list of", ignoreCase = true)) ordered += "culture"
        if (title.contains("university", ignoreCase = true)) ordered += "society"
        if (title.contains("city", ignoreCase = true)) ordered += "geography"
        if (title.contains("war", ignoreCase = true)) ordered += "history"

        if (ordered.isEmpty()) {
            ordered += "general"
        }

        tokenRegex.findAll(text).forEach { match ->
            val token = match.value.lowercase()
            if (token.length >= 4 && token !in stopwords) {
                ordered += token.replace(' ', '-')
            }
            if (ordered.size >= maxKeys) return@forEach
        }

        return ordered.take(maxKeys)
    }

    fun primaryTopic(title: String, summary: String, topicKey: String): String {
        val keys = preferenceKeys(title = title, summary = summary, topicKey = topicKey, maxKeys = 8)
        return keys.firstOrNull { it in stableTopics && it != "general" }
            ?: canonicalTopic(topicKey).ifBlank { "general" }
    }

    fun displayTags(
        title: String,
        summary: String,
        topicKey: String,
        bookmarked: Boolean,
        maxTags: Int = 6,
    ): List<String> {
        val ordered = linkedSetOf<String>()
        preferenceKeys(
            title = title,
            summary = summary,
            topicKey = topicKey,
            maxKeys = maxTags * 2,
        ).forEach { key ->
            if (key in stableTopics) {
                ordered += prettyTopic(key)
            }
        }

        if (title.contains("list of", ignoreCase = true)) ordered += "Lists"
        if (title.contains("university", ignoreCase = true)) ordered += "Education"
        if (bookmarked) ordered += "Saved"

        return ordered.take(maxTags)
    }

    fun prettyTopic(raw: String): String {
        return raw
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> token.lowercase().replaceFirstChar { it.titlecase() } }
            .ifBlank { "General" }
    }

    private fun canonicalTopic(rawTopic: String): String {
        val canonical = rawTopic
            .trim()
            .lowercase()
            .replace('_', '-')
            .replace(' ', '-')
            .replace(Regex("-+"), "-")
        return when (canonical) {
            "history-of" -> "history"
            "geography-of" -> "geography"
            "economy-of" -> "economics"
            "list-of" -> "culture"
            else -> canonical
        }
    }
}

