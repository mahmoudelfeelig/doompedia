package com.feelbachelor.doompedia.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "doompedia_settings")

data class UserSettings(
    val language: String = "en",
    val personalizationLevel: PersonalizationLevel = PersonalizationLevel.LOW,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentHex: String = "#0B6E5B",
    val wifiOnlyDownloads: Boolean = true,
    val manifestUrl: String = "",
    val installedPackVersion: Int = 0,
    val lastUpdateIso: String = "",
    val lastUpdateStatus: String = "",
)

class UserPreferencesStore(
    private val context: Context,
) {
    private val languageKey = stringPreferencesKey("language")
    private val personalizationKey = stringPreferencesKey("personalization_level")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val accentHexKey = stringPreferencesKey("accent_hex")
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only_downloads")
    private val manifestUrlKey = stringPreferencesKey("manifest_url")
    private val installedPackVersionKey = intPreferencesKey("installed_pack_version")
    private val lastUpdateIsoKey = stringPreferencesKey("last_update_iso")
    private val lastUpdateStatusKey = stringPreferencesKey("last_update_status")

    val settings: Flow<UserSettings> = context.dataStore.data.map(::toSettings)

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { prefs -> prefs[languageKey] = language }
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

    fun exportToJson(settings: UserSettings): String {
        val payload = JSONObject()
            .put("language", settings.language)
            .put("personalizationLevel", settings.personalizationLevel.name)
            .put("themeMode", settings.themeMode.name)
            .put("accentHex", settings.accentHex)
            .put("wifiOnlyDownloads", settings.wifiOnlyDownloads)
            .put("manifestUrl", settings.manifestUrl)
            .put("installedPackVersion", settings.installedPackVersion)
        return payload.toString(2)
    }

    suspend fun importFromJson(payload: String): Result<UserSettings> {
        return runCatching {
            val root = JSONObject(payload)
            context.dataStore.edit { prefs ->
                root.optString("language").takeIf { it.isNotBlank() }?.let { prefs[languageKey] = it }
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
                if (root.has("wifiOnlyDownloads")) {
                    prefs[wifiOnlyKey] = root.optBoolean("wifiOnlyDownloads", true)
                }
                root.optString("manifestUrl").takeIf { it.isNotBlank() }?.let { prefs[manifestUrlKey] = it.trim() }
                if (root.has("installedPackVersion")) {
                    prefs[installedPackVersionKey] = root.optInt("installedPackVersion", 0).coerceAtLeast(0)
                }
            }
            settings.first()
        }
    }

    private fun toSettings(prefs: Preferences): UserSettings {
        val level = prefs[personalizationKey]?.let {
            PersonalizationLevel.entries.firstOrNull { entry -> entry.name == it }
        } ?: PersonalizationLevel.LOW

        val theme = prefs[themeKey]?.let {
            ThemeMode.entries.firstOrNull { entry -> entry.name == it }
        } ?: ThemeMode.SYSTEM

        return UserSettings(
            language = prefs[languageKey] ?: "en",
            personalizationLevel = level,
            themeMode = theme,
            accentHex = prefs[accentHexKey] ?: "#0B6E5B",
            wifiOnlyDownloads = prefs[wifiOnlyKey] ?: true,
            manifestUrl = prefs[manifestUrlKey] ?: "",
            installedPackVersion = prefs[installedPackVersionKey] ?: 0,
            lastUpdateIso = prefs[lastUpdateIsoKey] ?: "",
            lastUpdateStatus = prefs[lastUpdateStatusKey] ?: "",
        )
    }
}
