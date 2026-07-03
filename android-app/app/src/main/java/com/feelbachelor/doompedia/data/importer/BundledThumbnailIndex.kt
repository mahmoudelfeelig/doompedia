package com.feelbachelor.doompedia.data.importer

import android.content.Context
import org.json.JSONObject

class BundledThumbnailIndex(context: Context) {
    private val pathsByPageId: Map<Long, String> = runCatching {
        val payload = context.assets
            .open("content/featured_thumbnails.json")
            .bufferedReader()
            .use { it.readText() }
        val articles = JSONObject(payload).optJSONArray("articles") ?: return@runCatching emptyMap()

        buildMap {
            for (index in 0 until articles.length()) {
                val article = articles.optJSONObject(index) ?: continue
                val pageId = article.optLong("page_id", -1L)
                val assetPath = article.optString("thumbnail_asset").trim()
                val hostedUrl = article.optString("thumbnail_url").trim()
                if (pageId > 0L) {
                    when {
                        assetPath.isNotBlank() -> put(pageId, "file:///android_asset/$assetPath")
                        hostedUrl.startsWith("https://") -> put(pageId, hostedUrl)
                    }
                }
            }
        }
    }.getOrDefault(emptyMap())

    fun assetUri(pageId: Long): String? = pathsByPageId[pageId]
}
