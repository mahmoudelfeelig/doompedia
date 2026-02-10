package com.feelbachelor.doompedia.ui

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.feelbachelor.doompedia.AppContainer
import com.feelbachelor.doompedia.data.importer.PackManifest
import com.feelbachelor.doompedia.data.repo.UserSettings
import com.feelbachelor.doompedia.data.repo.WikiRepository
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.FeedMode
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.RankedCard
import com.feelbachelor.doompedia.domain.ReadSort
import com.feelbachelor.doompedia.domain.SaveFolderSummary
import com.feelbachelor.doompedia.domain.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

data class PackOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val downloadSize: String,
    val installSize: String,
    val manifestUrl: String,
    val available: Boolean = true,
    val articleCount: Int = 0,
    val shardCount: Int = 0,
    val includedTopics: List<String> = emptyList(),
    val removable: Boolean = false,
)

data class FolderPickerState(
    val card: ArticleCard,
    val selectedFolderIds: Set<Long> = emptySet(),
)

data class MainUiState(
    val settings: UserSettings = UserSettings(),
    val query: String = "",
    val loading: Boolean = true,
    val updateInProgress: Boolean = false,
    val feed: List<RankedCard> = emptyList(),
    val searchResults: List<ArticleCard> = emptyList(),
    val folders: List<SaveFolderSummary> = emptyList(),
    val selectedFolderId: Long = WikiRepository.DEFAULT_BOOKMARKS_FOLDER_ID,
    val savedCards: List<ArticleCard> = emptyList(),
    val folderPicker: FolderPickerState? = null,
    val packCatalog: List<PackOption> = defaultPackCatalog(),
    val effectiveFeedMode: FeedMode = FeedMode.OFFLINE,
    val error: String? = null,
)

sealed interface UiEvent {
    data class OpenUrl(val url: String) : UiEvent
    data class Snackbar(val message: String) : UiEvent
    data class CopyToClipboard(val text: String, val message: String) : UiEvent
}

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()
    private val manifestJson = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            runCatching {
                container.bootstrapper.ensureSeedData()
                container.repository.ensureDefaults()
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message) }
            }
            refreshSavedData()
            refreshFeed()
        }

        viewModelScope.launch {
            var previousSettings: UserSettings? = null
            container.preferences.settings.collectLatest { settings ->
                val shouldRefreshFeed = previousSettings == null ||
                    previousSettings?.language != settings.language ||
                    previousSettings?.personalizationLevel != settings.personalizationLevel ||
                    previousSettings?.feedMode != settings.feedMode

                val shouldRefreshSaved = previousSettings == null ||
                    previousSettings?.language != settings.language ||
                    previousSettings?.readSort != settings.readSort

                _uiState.update {
                    it.copy(
                        settings = settings,
                        packCatalog = buildPackCatalog(settings.customPacksJson),
                        effectiveFeedMode = effectiveFeedMode(settings.feedMode),
                    )
                }
                previousSettings = settings

                if (shouldRefreshFeed) refreshFeed()
                if (shouldRefreshSaved) refreshSavedData()
            }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        viewModelScope.launch {
            if (value.isBlank()) {
                _uiState.update { it.copy(searchResults = emptyList()) }
                return@launch
            }

            val settings = _uiState.value.settings
            if (settings.feedMode == FeedMode.ONLINE && hasInternet()) {
                runCatching {
                    val remoteMatches = container.wikipediaApiClient.searchTitles(
                        language = settings.language,
                        query = value,
                        limit = 25,
                    )
                    container.repository.cacheOnlineArticles(remoteMatches)
                }
            }
            val results = container.repository.searchByTitle(settings.language, value)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun refreshFeed() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            _uiState.update { it.copy(loading = true, error = null) }
            val feed = runCatching {
                when {
                    settings.feedMode == FeedMode.ONLINE && hasInternet() -> {
                        val remote = container.wikipediaApiClient.fetchRandomSummaries(
                            language = settings.language,
                            count = 45,
                        )
                        if (remote.isNotEmpty()) {
                            container.repository.cacheOnlineArticles(remote)
                            val candidates = remote.map { row ->
                                ArticleCard(
                                    pageId = row.pageId,
                                    lang = row.lang,
                                    title = row.title,
                                    normalizedTitle = com.feelbachelor.doompedia.domain.normalizeSearch(row.title),
                                    summary = row.summary,
                                    wikiUrl = row.wikiUrl,
                                    topicKey = com.feelbachelor.doompedia.data.importer.TopicClassifier.normalizeTopic(
                                        rawTopic = "general",
                                        title = row.title,
                                        summary = row.summary,
                                    ),
                                    qualityScore = 0.55,
                                    isDisambiguation = false,
                                    sourceRevId = row.sourceRevId,
                                    updatedAt = row.updatedAt,
                                    bookmarked = false,
                                )
                            }
                            container.repository.rankCandidates(
                                language = settings.language,
                                personalizationLevel = settings.personalizationLevel,
                                candidates = candidates,
                                limit = 50,
                            )
                        } else {
                            container.repository.loadFeed(
                                language = settings.language,
                                personalizationLevel = settings.personalizationLevel,
                            )
                        }
                    }

                    settings.feedMode == FeedMode.ONLINE && !hasInternet() -> {
                        container.repository.loadFeed(
                            language = settings.language,
                            personalizationLevel = settings.personalizationLevel,
                        )
                    }

                    else -> {
                        container.repository.loadFeed(
                            language = settings.language,
                            personalizationLevel = settings.personalizationLevel,
                        )
                    }
                }
            }

            if (settings.feedMode == FeedMode.ONLINE && !hasInternet()) {
                _events.emit(UiEvent.Snackbar("No internet connection. Showing offline feed."))
            }

            feed.onSuccess { ranked ->
                _uiState.update {
                    it.copy(
                        feed = ranked,
                        loading = false,
                        effectiveFeedMode = effectiveFeedMode(settings.feedMode),
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    fun refreshSavedData() {
        viewModelScope.launch {
            runCatching {
                val settings = _uiState.value.settings
                val folders = container.repository.saveFolders(settings.language)
                val selected = if (folders.any { it.folderId == _uiState.value.selectedFolderId }) {
                    _uiState.value.selectedFolderId
                } else {
                    folders.firstOrNull()?.folderId ?: WikiRepository.DEFAULT_BOOKMARKS_FOLDER_ID
                }
                val cards = if (folders.isNotEmpty()) {
                    container.repository.savedCards(
                        folderId = selected,
                        language = settings.language,
                        readSort = settings.readSort,
                    )
                } else {
                    emptyList()
                }
                _uiState.update {
                    it.copy(
                        folders = folders,
                        selectedFolderId = selected,
                        savedCards = cards,
                    )
                }
            }.onFailure { error ->
                _events.emit(UiEvent.Snackbar(error.message ?: "Could not load saved folders"))
            }
        }
    }

    fun selectSavedFolder(folderId: Long) {
        viewModelScope.launch {
            runCatching {
                val settings = _uiState.value.settings
                val cards = container.repository.savedCards(
                    folderId = folderId,
                    language = settings.language,
                    readSort = settings.readSort,
                )
                _uiState.update {
                    it.copy(
                        selectedFolderId = folderId,
                        savedCards = cards,
                    )
                }
            }.onFailure { error ->
                _events.emit(UiEvent.Snackbar(error.message ?: "Could not load folder"))
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val created = runCatching { container.repository.createFolder(name) }.getOrDefault(false)
            if (!created) {
                _events.emit(UiEvent.Snackbar("Folder name is invalid or already exists"))
                return@launch
            }
            refreshSavedData()
            _events.emit(UiEvent.Snackbar("Folder created"))
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            val deleted = runCatching { container.repository.deleteFolder(folderId) }.getOrDefault(false)
            if (!deleted) {
                _events.emit(UiEvent.Snackbar("This folder cannot be removed"))
                return@launch
            }
            refreshSavedData()
            _events.emit(UiEvent.Snackbar("Folder removed"))
        }
    }

    fun showFolderPicker(card: ArticleCard) {
        viewModelScope.launch {
            val selected = runCatching {
                container.repository.selectedFolderIds(card.pageId)
                    .minus(WikiRepository.DEFAULT_READ_FOLDER_ID)
            }.getOrElse { emptySet() }
            _uiState.update {
                it.copy(
                    folderPicker = FolderPickerState(
                        card = card,
                        selectedFolderIds = selected,
                    )
                )
            }
        }
    }

    fun toggleFolderSelection(folderId: Long) {
        if (folderId == WikiRepository.DEFAULT_READ_FOLDER_ID) return
        _uiState.update { state ->
            val picker = state.folderPicker ?: return@update state
            val updated = picker.selectedFolderIds.toMutableSet().also { selected ->
                if (!selected.add(folderId)) selected.remove(folderId)
            }
            state.copy(folderPicker = picker.copy(selectedFolderIds = updated))
        }
    }

    fun hideFolderPicker() {
        _uiState.update { it.copy(folderPicker = null) }
    }

    fun applyFolderPickerSelection() {
        viewModelScope.launch {
            val picker = _uiState.value.folderPicker ?: return@launch
            runCatching {
                container.repository.setFoldersForArticle(
                    pageId = picker.card.pageId,
                    folderIds = picker.selectedFolderIds,
                )
            }.onSuccess {
                hideFolderPicker()
                refreshFeed()
                refreshSavedData()
                _events.emit(UiEvent.Snackbar("Saved folders updated"))
            }.onFailure { error ->
                _events.emit(UiEvent.Snackbar(error.message ?: "Could not update folders"))
            }
        }
    }

    fun onOpenCard(card: ArticleCard) {
        viewModelScope.launch {
            container.repository.recordOpen(card, _uiState.value.settings.personalizationLevel)
            _events.emit(UiEvent.OpenUrl(card.wikiUrl))
            refreshFeed()
            if (_uiState.value.selectedFolderId == WikiRepository.DEFAULT_READ_FOLDER_ID) {
                refreshSavedData()
            }
        }
    }

    fun onMoreLike(card: ArticleCard) {
        viewModelScope.launch {
            container.repository.recordMoreLike(card, _uiState.value.settings.personalizationLevel)
            _events.emit(UiEvent.Snackbar("Feed updated to show more ${card.topicKey}"))
            refreshFeed()
        }
    }

    fun onLessLike(card: ArticleCard) {
        viewModelScope.launch {
            container.repository.recordLessLike(card, _uiState.value.settings.personalizationLevel)
            _events.emit(UiEvent.Snackbar("Feed updated to show less ${card.topicKey}"))
            refreshFeed()
        }
    }

    fun onToggleBookmark(card: ArticleCard) {
        viewModelScope.launch {
            val bookmarked = container.repository.toggleBookmark(card.pageId)
            val message = if (bookmarked) "Saved to Bookmarks" else "Removed from Bookmarks"
            _events.emit(UiEvent.Snackbar(message))
            refreshFeed()
            refreshSavedData()
        }
    }

    fun setPersonalization(level: PersonalizationLevel) {
        viewModelScope.launch {
            container.preferences.setPersonalization(level)
        }
    }

    fun setFeedMode(mode: FeedMode) {
        viewModelScope.launch {
            container.preferences.setFeedMode(mode)
            if (mode == FeedMode.ONLINE && !hasInternet()) {
                _events.emit(UiEvent.Snackbar("No internet connection. Offline mode will be used automatically."))
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            container.preferences.setTheme(mode)
        }
    }

    fun setAccentHex(hex: String) {
        viewModelScope.launch {
            container.preferences.setAccentHex(hex)
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            container.preferences.setFontScale(scale)
        }
    }

    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch {
            container.preferences.setHighContrast(enabled)
        }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch {
            container.preferences.setReduceMotion(enabled)
        }
    }

    fun setReadSort(sort: ReadSort) {
        viewModelScope.launch {
            container.preferences.setReadSort(sort)
            if (_uiState.value.selectedFolderId == WikiRepository.DEFAULT_READ_FOLDER_ID) {
                refreshSavedData()
            }
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            container.preferences.setWifiOnly(enabled)
        }
    }

    fun setManifestUrl(url: String) {
        viewModelScope.launch {
            container.preferences.setManifestUrl(url)
        }
    }

    fun choosePack(pack: PackOption) {
        if (!pack.available || pack.manifestUrl.isBlank()) return
        viewModelScope.launch {
            container.preferences.setManifestUrl(pack.manifestUrl)
            _events.emit(UiEvent.Snackbar("${pack.title} selected (${pack.articleCount} articles)"))
        }
    }

    fun addPackByManifestUrl(rawUrl: String) {
        viewModelScope.launch {
            val url = rawUrl.trim()
            if (url.isBlank()) {
                _events.emit(UiEvent.Snackbar("Enter a manifest URL"))
                return@launch
            }

            runCatching {
                val payload = withContext(Dispatchers.IO) {
                    URL(url).openStream().bufferedReader().use { it.readText() }
                }
                val manifest = manifestJson.decodeFromString(PackManifest.serializer(), payload)
                val merged = upsertCustomPack(_uiState.value.settings.customPacksJson, manifest, url)
                container.preferences.setCustomPacksJson(merged)
            }.onSuccess {
                _events.emit(UiEvent.Snackbar("Pack added"))
            }.onFailure { error ->
                _events.emit(UiEvent.Snackbar(error.message ?: "Could not add pack from manifest"))
            }
        }
    }

    fun removePack(pack: PackOption) {
        if (!pack.removable) return
        viewModelScope.launch {
            runCatching {
                val updated = removeCustomPack(_uiState.value.settings.customPacksJson, pack.id)
                container.preferences.setCustomPacksJson(updated)
            }.onSuccess {
                _events.emit(UiEvent.Snackbar("${pack.title} removed"))
            }.onFailure { error ->
                _events.emit(UiEvent.Snackbar(error.message ?: "Could not remove pack"))
            }
        }
    }

    fun exportSettings() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            val payload = container.preferences.exportToJson(settings)
            _events.emit(
                UiEvent.CopyToClipboard(
                    text = payload,
                    message = "Settings JSON copied to clipboard",
                )
            )
        }
    }

    fun importSettings(payload: String) {
        viewModelScope.launch {
            val result = container.preferences.importFromJson(payload)
            result.onSuccess {
                _events.emit(UiEvent.Snackbar("Settings imported"))
                refreshFeed()
                refreshSavedData()
            }.onFailure { error ->
                _events.emit(UiEvent.Snackbar(error.message ?: "Could not import settings JSON"))
            }
        }
    }

    fun exportSelectedFolder() {
        viewModelScope.launch {
            val selectedFolderId = _uiState.value.selectedFolderId
            if (selectedFolderId == WikiRepository.DEFAULT_READ_FOLDER_ID) {
                _events.emit(UiEvent.Snackbar("Read activity folder cannot be exported"))
                return@launch
            }

            runCatching {
                container.repository.exportFolders(setOf(selectedFolderId))
            }.onSuccess { payload ->
                _events.emit(
                    UiEvent.CopyToClipboard(
                        text = payload,
                        message = "Selected folder JSON copied to clipboard",
                    )
                )
            }.onFailure { error ->
                _events.emit(UiEvent.Snackbar(error.message ?: "Could not export selected folder"))
            }
        }
    }

    fun exportAllFolders() {
        viewModelScope.launch {
            runCatching {
                container.repository.exportFolders()
            }.onSuccess { payload ->
                _events.emit(
                    UiEvent.CopyToClipboard(
                        text = payload,
                        message = "All folders JSON copied to clipboard",
                    )
                )
            }.onFailure { error ->
                _events.emit(UiEvent.Snackbar(error.message ?: "Could not export folders"))
            }
        }
    }

    fun importFolders(payload: String) {
        viewModelScope.launch {
            runCatching {
                container.repository.importFolders(payload)
            }.onSuccess { linked ->
                refreshSavedData()
                refreshFeed()
                _events.emit(UiEvent.Snackbar("Folder import complete ($linked article links applied)"))
            }.onFailure { error ->
                _events.emit(UiEvent.Snackbar(error.message ?: "Could not import folder JSON"))
            }
        }
    }

    fun openExternalUrl(url: String) {
        viewModelScope.launch {
            _events.emit(UiEvent.OpenUrl(url))
        }
    }

    fun checkForUpdatesNow() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            if (settings.manifestUrl.isBlank()) {
                _events.emit(UiEvent.Snackbar("Set a manifest URL first"))
                return@launch
            }

            _uiState.update { it.copy(updateInProgress = true) }
            val outcome = runCatching {
                container.updateService.checkAndApply(
                    manifestUrl = settings.manifestUrl,
                    wifiOnly = settings.wifiOnlyDownloads,
                    installedVersion = settings.installedPackVersion,
                )
            }.getOrElse { error ->
                com.feelbachelor.doompedia.data.update.PackUpdateResult(
                    status = com.feelbachelor.doompedia.data.update.PackUpdateStatus.FAILED,
                    installedVersion = settings.installedPackVersion,
                    message = error.message ?: "Update failed",
                )
            }

            runCatching {
                container.preferences.setInstalledPackVersion(outcome.installedVersion)
                container.preferences.setLastUpdate(
                    timestampIso = java.time.Instant.now().toString(),
                    status = outcome.message,
                )
            }

            _uiState.update { it.copy(updateInProgress = false) }
            _events.emit(UiEvent.Snackbar(outcome.message))
            refreshFeed()
            refreshSavedData()
        }
    }

    private fun hasInternet(): Boolean {
        val manager = container.appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun effectiveFeedMode(requested: FeedMode): FeedMode {
        return if (requested == FeedMode.ONLINE && !hasInternet()) FeedMode.OFFLINE else requested
    }
}

class MainViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(container) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun defaultPackCatalog(customPacksJson: String = "[]"): List<PackOption> {
    val defaults = listOf(
        PackOption(
            id = "en-core-1m",
            title = "English Core 1M",
            subtitle = "General encyclopedia pack. Includes science, geography, history, culture, and biographies.",
            downloadSize = "~380 MB (gzip) / ~396 MB raw",
            installSize = "~1.3 GB",
            manifestUrl = "https://packs.example.invalid/packs/en-core-1m/v1/manifest.json",
            available = true,
            articleCount = 1_000_000,
            shardCount = 25,
            includedTopics = listOf("General", "Science", "History", "Geography", "Culture", "Biography"),
        ),
        PackOption(
            id = "en-science-250k",
            title = "English Science 250K",
            subtitle = "Focused pack for science and technology topics",
            downloadSize = "~95 MB (gzip)",
            installSize = "~320 MB",
            manifestUrl = "",
            available = false,
            articleCount = 250_000,
            shardCount = 7,
            includedTopics = listOf("Science", "Technology", "Mathematics"),
        ),
        PackOption(
            id = "en-history-250k",
            title = "English History 250K",
            subtitle = "Focused pack for history and biography topics",
            downloadSize = "~90 MB (gzip)",
            installSize = "~300 MB",
            manifestUrl = "",
            available = false,
            articleCount = 250_000,
            shardCount = 7,
            includedTopics = listOf("History", "Biography", "Politics"),
        ),
    )
    return defaults + parseCustomPacks(customPacksJson)
}

private fun buildPackCatalog(customPacksJson: String): List<PackOption> {
    return defaultPackCatalog(customPacksJson)
}

private fun parseCustomPacks(payload: String): List<PackOption> {
    return runCatching {
        val json = JSONArray(payload)
        buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                val subtitle = item.optString("subtitle").trim()
                val manifestUrl = item.optString("manifestUrl").trim()
                if (id.isBlank() || title.isBlank() || manifestUrl.isBlank()) continue

                val includedTopics = mutableListOf<String>()
                val topics = item.optJSONArray("includedTopics") ?: JSONArray()
                for (topicIndex in 0 until topics.length()) {
                    val topic = topics.optString(topicIndex).trim()
                    if (topic.isNotBlank()) includedTopics += topic
                }

                add(
                    PackOption(
                        id = id,
                        title = title,
                        subtitle = subtitle.ifBlank { "Custom pack" },
                        downloadSize = item.optString("downloadSize").ifBlank { "Unknown" },
                        installSize = item.optString("installSize").ifBlank { "Unknown" },
                        manifestUrl = manifestUrl,
                        available = true,
                        articleCount = item.optInt("articleCount", 0).coerceAtLeast(0),
                        shardCount = item.optInt("shardCount", 0).coerceAtLeast(0),
                        includedTopics = includedTopics,
                        removable = true,
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun upsertCustomPack(existingJson: String, manifest: PackManifest, manifestUrl: String): String {
    val current = JSONArray(existingJson)
    val next = JSONArray()
    val topicList = if (manifest.packTags.isNotEmpty()) {
        manifest.packTags.take(8).map(::prettyTopicLabel)
    } else {
        manifest.topicDistribution.keys
            .sortedByDescending { manifest.topicDistribution[it] ?: 0 }
            .take(8)
            .map(::prettyTopicLabel)
    }
    val downloadBytes = manifest.shards.sumOf { it.bytes }
    val generated = JSONObject()
        .put("id", manifest.packId)
        .put("title", manifest.packId.replace('-', ' ').replaceFirstChar { it.titlecase() })
        .put("subtitle", manifest.description?.takeIf { it.isNotBlank() } ?: "Pack imported from manifest")
        .put("manifestUrl", manifestUrl)
        .put("downloadSize", formatBytes(downloadBytes))
        .put("installSize", "Varies by device")
        .put("articleCount", manifest.recordCount)
        .put("shardCount", manifest.shards.size)
        .put("includedTopics", JSONArray(topicList))

    for (index in 0 until current.length()) {
        val item = current.optJSONObject(index) ?: continue
        if (item.optString("id") == manifest.packId) continue
        next.put(item)
    }
    next.put(generated)
    return next.toString()
}

private fun removeCustomPack(existingJson: String, packId: String): String {
    val current = JSONArray(existingJson)
    val next = JSONArray()
    for (index in 0 until current.length()) {
        val item = current.optJSONObject(index) ?: continue
        if (item.optString("id") == packId) continue
        next.put(item)
    }
    return next.toString()
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "Unknown"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format("%.2f GB", mb / 1024.0)
    } else {
        String.format("%.0f MB", mb)
    }
}

private fun prettyTopicLabel(raw: String): String {
    return raw
        .replace('-', ' ')
        .replace('_', ' ')
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token -> token.lowercase().replaceFirstChar { it.titlecase() } }
        .ifBlank { "General" }
}
