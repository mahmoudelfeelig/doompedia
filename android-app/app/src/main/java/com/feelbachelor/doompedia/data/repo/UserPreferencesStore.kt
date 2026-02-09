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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "doompedia_settings")

data class UserSettings(
    val language: String = "en",
    val personalizationLevel: PersonalizationLevel = PersonalizationLevel.LOW,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
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
            wifiOnlyDownloads = prefs[wifiOnlyKey] ?: true,
            manifestUrl = prefs[manifestUrlKey] ?: "",
            installedPackVersion = prefs[installedPackVersionKey] ?: 0,
            lastUpdateIso = prefs[lastUpdateIsoKey] ?: "",
            lastUpdateStatus = prefs[lastUpdateStatusKey] ?: "",
        )
    }
}
