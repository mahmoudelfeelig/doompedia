package com.feelbachelor.doompedia.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.ReadSort
import com.feelbachelor.doompedia.domain.ThemeMode
import com.feelbachelor.doompedia.domain.FeedMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "doompedia_settings")

data class UserSettings(
    val language: String = "en",
    val feedMode: FeedMode = FeedMode.OFFLINE,
    val personalizationLevel: PersonalizationLevel = PersonalizationLevel.LOW,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentHex: String = "#0B6E5B",
    val fontScale: Float = 1.0f,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val readSort: ReadSort = ReadSort.NEWEST_FIRST,
    val wifiOnlyDownloads: Boolean = true,
    val manifestUrl: String = "",
    val installedPackVersion: Int = 0,
    val lastUpdateIso: String = "",
    val lastUpdateStatus: String = "",
    val customPacksJson: String = "[]",
)

class UserPreferencesStore(
    private val context: Context,
) {
    private val languageKey = stringPreferencesKey("language")
    private val feedModeKey = stringPreferencesKey("feed_mode")
    private val personalizationKey = stringPreferencesKey("personalization_level")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val accentHexKey = stringPreferencesKey("accent_hex")
    private val fontScaleKey = floatPreferencesKey("font_scale")
    private val highContrastKey = booleanPreferencesKey("high_contrast")
    private val reduceMotionKey = booleanPreferencesKey("reduce_motion")
    private val readSortKey = stringPreferencesKey("read_sort")
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only_downloads")
    private val manifestUrlKey = stringPreferencesKey("manifest_url")
    private val installedPackVersionKey = intPreferencesKey("installed_pack_version")
    private val lastUpdateIsoKey = stringPreferencesKey("last_update_iso")
    private val lastUpdateStatusKey = stringPreferencesKey("last_update_status")
    private val customPacksKey = stringPreferencesKey("custom_packs_json")

    val settings: Flow<UserSettings> = context.dataStore.data.map(::toSettings)

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { prefs -> prefs[languageKey] = language }
    }

    suspend fun setFeedMode(feedMode: FeedMode) {
        context.dataStore.edit { prefs -> prefs[feedModeKey] = feedMode.name }
    }

    suspend fun setPersonalization(level: PersonalizationLevel) {
        context.dataStore.edit { prefs -> prefs[personalizationKey] = level.name }
    }

    suspend fun setTheme(themeMode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[themeKey] = themeMode.name }
    }

    suspend fun setAccentHex(hex: String) {
        context.dataStore.edit { prefs -> prefs[accentHexKey] = hex.trim() }
    }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[fontScaleKey] = scale.coerceIn(0.85f, 1.35f)
        }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[highContrastKey] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[reduceMotionKey] = enabled }
    }

    suspend fun setReadSort(sort: ReadSort) {
        context.dataStore.edit { prefs -> prefs[readSortKey] = sort.name }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[wifiOnlyKey] = enabled }
    }

    suspend fun setManifestUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[manifestUrlKey] = url.trim() }
    }

    suspend fun setInstalledPackVersion(version: Int) {
        context.dataStore.edit { prefs -> prefs[installedPackVersionKey] = version }
    }

    suspend fun setLastUpdate(timestampIso: String, status: String) {
        context.dataStore.edit { prefs ->
            prefs[lastUpdateIsoKey] = timestampIso
            prefs[lastUpdateStatusKey] = status
        }
    }

    suspend fun setCustomPacksJson(payload: String) {
        context.dataStore.edit { prefs -> prefs[customPacksKey] = payload }
    }

    fun exportToJson(settings: UserSettings): String {
        val payload = JSONObject()
            .put("language", settings.language)
            .put("feedMode", settings.feedMode.name)
            .put("personalizationLevel", settings.personalizationLevel.name)
            .put("themeMode", settings.themeMode.name)
            .put("accentHex", settings.accentHex)
            .put("fontScale", settings.fontScale.toDouble())
            .put("highContrast", settings.highContrast)
            .put("reduceMotion", settings.reduceMotion)
            .put("readSort", settings.readSort.name)
            .put("wifiOnlyDownloads", settings.wifiOnlyDownloads)
            .put("manifestUrl", settings.manifestUrl)
            .put("installedPackVersion", settings.installedPackVersion)
            .put("customPacksJson", settings.customPacksJson)
        return payload.toString(2)
    }

    suspend fun importFromJson(payload: String): Result<UserSettings> {
        return runCatching {
            val root = JSONObject(payload)
            context.dataStore.edit { prefs ->
                root.optString("language").takeIf { it.isNotBlank() }?.let { prefs[languageKey] = it }
                root.optString("feedMode")
                    .uppercase()
                    .let { raw -> FeedMode.entries.firstOrNull { it.name == raw } }
                    ?.let { prefs[feedModeKey] = it.name }
                root.optString("personalizationLevel")
                    .uppercase()
                    .let { raw ->
                        PersonalizationLevel.entries.firstOrNull { it.name == raw }
                    }?.let { prefs[personalizationKey] = it.name }
                root.optString("themeMode")
                    .uppercase()
                    .let { raw ->
                        ThemeMode.entries.firstOrNull { it.name == raw }
                    }?.let { prefs[themeKey] = it.name }
                root.optString("accentHex").takeIf { it.isNotBlank() }?.let { prefs[accentHexKey] = it.trim() }
                if (root.has("fontScale")) {
                    val scale = root.optDouble("fontScale", 1.0).toFloat().coerceIn(0.85f, 1.35f)
                    prefs[fontScaleKey] = scale
                }
                if (root.has("highContrast")) {
                    prefs[highContrastKey] = root.optBoolean("highContrast", false)
                }
                if (root.has("reduceMotion")) {
                    prefs[reduceMotionKey] = root.optBoolean("reduceMotion", false)
                }
                root.optString("readSort")
                    .uppercase()
                    .let { raw ->
                        ReadSort.entries.firstOrNull { it.name == raw }
                    }?.let { prefs[readSortKey] = it.name }
                if (root.has("wifiOnlyDownloads")) {
                    prefs[wifiOnlyKey] = root.optBoolean("wifiOnlyDownloads", true)
                }
                root.optString("manifestUrl").takeIf { it.isNotBlank() }?.let { prefs[manifestUrlKey] = it.trim() }
                if (root.has("installedPackVersion")) {
                    prefs[installedPackVersionKey] = root.optInt("installedPackVersion", 0).coerceAtLeast(0)
                }
                root.optString("customPacksJson").takeIf { it.isNotBlank() }?.let { prefs[customPacksKey] = it.trim() }
            }
            settings.first()
        }
    }

    private fun toSettings(prefs: Preferences): UserSettings {
        val level = prefs[personalizationKey]?.let {
            PersonalizationLevel.entries.firstOrNull { entry -> entry.name == it }
        } ?: PersonalizationLevel.LOW

        val feedMode = prefs[feedModeKey]?.let {
            FeedMode.entries.firstOrNull { entry -> entry.name == it }
        } ?: FeedMode.OFFLINE

        val theme = prefs[themeKey]?.let {
            ThemeMode.entries.firstOrNull { entry -> entry.name == it }
        } ?: ThemeMode.SYSTEM

        val readSort = prefs[readSortKey]?.let {
            ReadSort.entries.firstOrNull { entry -> entry.name == it }
        } ?: ReadSort.NEWEST_FIRST

        return UserSettings(
            language = prefs[languageKey] ?: "en",
            feedMode = feedMode,
            personalizationLevel = level,
            themeMode = theme,
            accentHex = prefs[accentHexKey] ?: "#0B6E5B",
            fontScale = (prefs[fontScaleKey] ?: 1.0f).coerceIn(0.85f, 1.35f),
            highContrast = prefs[highContrastKey] ?: false,
            reduceMotion = prefs[reduceMotionKey] ?: false,
            readSort = readSort,
            wifiOnlyDownloads = prefs[wifiOnlyKey] ?: true,
            manifestUrl = prefs[manifestUrlKey] ?: "",
            installedPackVersion = prefs[installedPackVersionKey] ?: 0,
            lastUpdateIso = prefs[lastUpdateIsoKey] ?: "",
            lastUpdateStatus = prefs[lastUpdateStatusKey] ?: "",
            customPacksJson = prefs[customPacksKey] ?: "[]",
        )
    }
}
