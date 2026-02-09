package com.feelbachelor.doompedia.ranking

import android.content.Context
import kotlinx.serialization.json.Json

class RankingConfigLoader(
    private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun load(): RankingConfig {
        val payload = context.assets
            .open("content/ranking-config.v1.json")
            .bufferedReader()
            .use { it.readText() }
        return json.decodeFromString(RankingConfig.serializer(), payload)
    }
}
