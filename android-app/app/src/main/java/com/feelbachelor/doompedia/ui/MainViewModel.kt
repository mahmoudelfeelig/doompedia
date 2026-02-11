package com.feelbachelor.doompedia.ui

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.feelbachelor.doompedia.AppContainer
import com.feelbachelor.doompedia.data.importer.PackManifest
import com.feelbachelor.doompedia.data.repo.UserSettings
import com.feelbachelor.doompedia.data.repo.WikiRepository
import com.feelbachelor.doompedia.data.update.PackUpdateProgress
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.CardKeywords
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

data class UpdateProgressUi(
    val phase: String = "",
    val percent: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val detail: String = "",
)

data class ImagePrefetchUi(
    val running: Boolean = false,
    val scanned: Int = 0,
    val total: Int = 0,
    val downloaded: Int = 0,
)

data class FolderPickerState(
    val card: ArticleCard,
    val selectedFolderIds: Set<Long> = emptySet(),
)

data class MainUiState(
    val settings: UserSettings = UserSettings(),
    val query: String = "",
    val loading: Boolean = true,
    val loadingMoreFeed: Boolean = false,
    val feedHasMore: Boolean = true,
    val updateInProgress: Boolean = false,
    val feed: List<RankedCard> = emptyList(),
    val searchResults: List<ArticleCard> = emptyList(),
    val folders: List<SaveFolderSummary> = emptyList(),
    val selectedFolderId: Long = WikiRepository.DEFAULT_BOOKMARKS_FOLDER_ID,
    val savedCards: List<ArticleCard> = emptyList(),
    val folderPicker: FolderPickerState? = null,
    val packCatalog: List<PackOption> = defaultPackCatalog(),
    val effectiveFeedMode: FeedMode = FeedMode.OFFLINE,
    val updateProgress: UpdateProgressUi? = null,
    val imagePrefetch: ImagePrefetchUi = ImagePrefetchUi(),
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
    private val imageUrlCache = linkedMapOf<Long, String?>()
    private val imageUrlMutex = Mutex()
    private val offlineFeedPageSize = 80
    private val onlineFeedPageSize = 45
    private var nextOfflineFeedOffset = 0
    private var offlineFeedEndReached = false

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

    fun refreshFeedManual() {
        refreshFeed()
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        viewModelScope.launch {
            if (value.isBlank()) {
                _uiState.update { it.copy(searchResults = emptyList()) }
                return@launch
            }

            val settings = _uiState.value.settings
            if (settings.feedMode == FeedMode.ONLINE) {
                runCatching {
                    val remoteMatches = container.wikipediaApiClient.searchTitles(
                        language = settings.language,
                        query = value,
                        limit = 25,
                    )
                    if (remoteMatches.isNotEmpty()) {
                        container.repository.cacheOnlineArticles(remoteMatches)
                    }
                }
            }
            val results = container.repository.searchByTitle(settings.language, value)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun refreshFeed() {
        viewModelScope.launch {
            loadFeedChunk(reset = true)
        }
    }

    fun loadMoreFeed() {
        val state = _uiState.value
        if (state.query.isNotBlank()) return
        if (state.loading || state.loadingMoreFeed || !state.feedHasMore) return

        viewModelScope.launch {
            loadFeedChunk(reset = false)
        }
    }

    private suspend fun loadFeedChunk(reset: Boolean) {
        val settings = _uiState.value.settings
        val onlineRequested = settings.feedMode == FeedMode.ONLINE
        val internetDetected = hasInternet()

        if (reset) {
            nextOfflineFeedOffset = 0
            offlineFeedEndReached = false
            _uiState.update {
                it.copy(
                    loading = true,
                    loadingMoreFeed = false,
                    feedHasMore = true,
                    feed = emptyList(),
                    error = null,
                )
            }
        } else {
            _uiState.update { it.copy(loadingMoreFeed = true, error = null) }
        }

        suspend fun loadOfflinePage(): List<RankedCard> {
            val initial = container.repository.loadFeedPage(
                language = settings.language,
                personalizationLevel = settings.personalizationLevel,
                offset = nextOfflineFeedOffset,
                limit = offlineFeedPageSize,
            )
            if (initial.isNotEmpty() || nextOfflineFeedOffset == 0) {
                return initial
            }

            // Reached end of local dataset; wrap to the beginning so feed can keep scrolling.
            nextOfflineFeedOffset = 0
            return container.repository.loadFeedPage(
                language = settings.language,
                personalizationLevel = settings.personalizationLevel,
                offset = 0,
                limit = offlineFeedPageSize,
            )
        }

        var usedOnlineData = false
        var usedOfflineData = false
        var onlineFetchFailed = false
        val feed = runCatching {
            when {
                onlineRequested -> {
                    val remote = runCatching {
                        container.wikipediaApiClient.fetchRandomSummaries(
                            language = settings.language,
                            count = onlineFeedPageSize,
                        )
                    }.onFailure {
                        onlineFetchFailed = true
                    }.getOrDefault(emptyList())
                    if (remote.isNotEmpty()) {
                        usedOnlineData = true
                        runCatching {
                            container.repository.cacheOnlineArticles(remote)
                        }
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
                        runCatching {
                            container.repository.rankCandidates(
                                language = settings.language,
                                personalizationLevel = settings.personalizationLevel,
                                candidates = candidates,
                                limit = onlineFeedPageSize,
                            )
                        }.getOrElse {
                            candidates.map { card ->
                                RankedCard(
                                    card = card,
                                    score = 0.0,
                                    why = "Live article",
                                )
                            }
                        }
                    } else {
                        usedOfflineData = true
                        loadOfflinePage()
                    }
                }

                else -> {
                    usedOfflineData = true
                    loadOfflinePage()
                }
            }
        }

        if (onlineRequested && !internetDetected && reset) {
            _events.emit(UiEvent.Snackbar("No internet detected. Trying live feed, then falling back to offline."))
        }
        if (onlineRequested && onlineFetchFailed && reset) {
            _events.emit(UiEvent.Snackbar("Could not fetch live feed. Showing offline/cached articles."))
        }

        feed.onSuccess { ranked ->
            val prior = if (reset) emptyList() else _uiState.value.feed

            if (usedOfflineData) {
                if (ranked.isEmpty()) {
                    offlineFeedEndReached = prior.isEmpty()
                } else {
                    nextOfflineFeedOffset += offlineFeedPageSize
                    offlineFeedEndReached = false
                }
            }

            _uiState.update {
                val merged = if (reset) ranked else (prior + ranked).takeLast(1_200)
                it.copy(
                    feed = merged,
                    loading = false,
                    loadingMoreFeed = false,
                    feedHasMore = if (usedOnlineData) true else (!offlineFeedEndReached || merged.isNotEmpty()),
                    effectiveFeedMode = effectiveFeedMode(settings.feedMode),
                )
            }

            if (reset && ranked.isEmpty()) {
                viewModelScope.launch {
                    _events.emit(
                        UiEvent.Snackbar(
                            "No articles available. Check connection for Online mode or download an offline pack."
                        )
                    )
                }
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    loading = false,
                    loadingMoreFeed = false,
                    error = error.message,
                )
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
            val keywords = CardKeywords.preferenceKeys(
                title = card.title,
                summary = card.summary,
                topicKey = card.topicKey,
                maxKeys = 10,
            ).joinToString(", ") { CardKeywords.prettyTopic(it) }
            _events.emit(UiEvent.Snackbar("Feed updated to show more like: $keywords"))
        }
    }

    fun onLessLike(card: ArticleCard) {
        viewModelScope.launch {
            container.repository.recordLessLike(card, _uiState.value.settings.personalizationLevel)
            val keywords = CardKeywords.preferenceKeys(
                title = card.title,
                summary = card.summary,
                topicKey = card.topicKey,
                maxKeys = 10,
            ).joinToString(", ") { CardKeywords.prettyTopic(it) }
            _events.emit(UiEvent.Snackbar("Feed updated to show less like: $keywords"))
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

    fun unsaveFromSelectedFolder(card: ArticleCard) {
        viewModelScope.launch {
            val selectedFolderId = _uiState.value.selectedFolderId
            if (selectedFolderId == WikiRepository.DEFAULT_READ_FOLDER_ID) {
                _events.emit(UiEvent.Snackbar("Read activity cannot be edited"))
                return@launch
            }

            val removed = if (selectedFolderId == WikiRepository.DEFAULT_BOOKMARKS_FOLDER_ID) {
                !container.repository.toggleBookmark(card.pageId)
            } else {
                container.repository.removeFromFolder(selectedFolderId, card.pageId)
            }

            val message = if (removed) {
                "Removed from ${_uiState.value.folders.firstOrNull { it.folderId == selectedFolderId }?.name ?: "folder"}"
            } else {
                "Nothing changed"
            }
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
                _events.emit(UiEvent.Snackbar("No internet detected right now. Live mode will retry and fall back if needed."))
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

    fun setDownloadPreviewImages(enabled: Boolean) {
        viewModelScope.launch {
            container.preferences.setDownloadPreviewImages(enabled)
        }
    }

    fun downloadImagesNow() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            if (!settings.downloadPreviewImages) {
                _events.emit(UiEvent.Snackbar("Enable 'Download preview images' first"))
                return@launch
            }
            if (!hasInternet()) {
                _events.emit(UiEvent.Snackbar("Internet is required to prefetch images"))
                return@launch
            }
            if (_uiState.value.imagePrefetch.running) return@launch

            val total = runCatching { container.repository.articleCount(settings.language) }.getOrDefault(0)
            if (total <= 0) {
                _events.emit(UiEvent.Snackbar("No local articles found to prefetch images for"))
                return@launch
            }

            _uiState.update {
                it.copy(
                    imagePrefetch = ImagePrefetchUi(
                        running = true,
                        scanned = 0,
                        total = total,
                        downloaded = 0,
                    )
                )
            }

            val imageLoader = Coil.imageLoader(container.appContext)
            val batchSize = 120
            var offset = 0
            var scanned = 0
            var downloaded = 0

            while (offset < total) {
                val cards = runCatching {
                    container.repository.feedPageCards(
                        language = settings.language,
                        offset = offset,
                        limit = batchSize,
                    )
                }.getOrDefault(emptyList())

                if (cards.isEmpty()) break

                cards.forEach { card ->
                    scanned += 1
                    val imageUrl = resolveThumbnailUrl(card)
                    if (!imageUrl.isNullOrBlank()) {
                        val fetched = runCatching {
                            withContext(Dispatchers.IO) {
                                val request = ImageRequest.Builder(container.appContext)
                                    .data(imageUrl)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .networkCachePolicy(CachePolicy.ENABLED)
                                    .build()
                                imageLoader.execute(request)
                            }
                        }.isSuccess
                        if (fetched) {
                            downloaded += 1
                        }
                    }
                }

                offset += batchSize
                _uiState.update {
                    it.copy(
                        imagePrefetch = ImagePrefetchUi(
                            running = true,
                            scanned = scanned,
                            total = total,
                            downloaded = downloaded,
                        )
                    )
                }
            }

            _uiState.update {
                it.copy(
                    imagePrefetch = it.imagePrefetch.copy(running = false)
                )
            }
            _events.emit(
                UiEvent.Snackbar(
                    "Image prefetch complete: $downloaded cached from $scanned scanned articles"
                )
            )
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
            _events.emit(
                UiEvent.Snackbar(
                    "${pack.title} selected (${pack.articleCount} articles). Tap Download / update to start downloading."
                )
            )
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

            _uiState.update {
                it.copy(
                    updateInProgress = true,
                    updateProgress = UpdateProgressUi(phase = "Starting", percent = 0f),
                )
            }
            val currentArticleCount = runCatching { container.repository.articleCount() }.getOrDefault(0)
            val selectedPack = _uiState.value.packCatalog.firstOrNull {
                it.manifestUrl.trim().equals(settings.manifestUrl.trim(), ignoreCase = true)
            }
            val expectedArticleCount = selectedPack?.articleCount ?: 0
            val appearsIncomplete = expectedArticleCount > 0 &&
                currentArticleCount < (expectedArticleCount * 0.90).toInt()
            val effectiveInstalledVersion =
                if (currentArticleCount <= 0 || appearsIncomplete) 0 else settings.installedPackVersion
            val effectiveInstalledSignature =
                if (currentArticleCount <= 0 || appearsIncomplete) "" else settings.installedPackSignature

            if (appearsIncomplete) {
                _events.emit(
                    UiEvent.Snackbar(
                        "Local data looks incomplete for this pack. Forcing full download."
                    )
                )
            }

            val outcome = runCatching {
                container.updateService.checkAndApply(
                    manifestUrl = settings.manifestUrl,
                    // Manual check should run immediately when user taps the button.
                    // Wi-Fi-only is still enforced for background/automatic checks.
                    wifiOnly = false,
                    installedVersion = effectiveInstalledVersion,
                    installedSignature = effectiveInstalledSignature,
                    onProgress = { progress ->
                        _uiState.update {
                            it.copy(updateProgress = progress.toUi())
                        }
                    }
                )
            }.getOrElse { error ->
                com.feelbachelor.doompedia.data.update.PackUpdateResult(
                    status = com.feelbachelor.doompedia.data.update.PackUpdateStatus.FAILED,
                    installedVersion = effectiveInstalledVersion,
                    installedSignature = effectiveInstalledSignature,
                    message = error.message ?: "Update failed",
                )
            }

            runCatching {
                container.preferences.setInstalledPackInfo(
                    version = outcome.installedVersion,
                    signature = outcome.installedSignature,
                )
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

    suspend fun resolveThumbnailUrl(card: ArticleCard): String? {
        val settings = _uiState.value.settings
        if (!settings.downloadPreviewImages) return null

        imageUrlMutex.withLock {
            if (imageUrlCache.containsKey(card.pageId)) {
                return imageUrlCache[card.pageId]
            }
        }

        val fetched = runCatching {
            container.wikipediaImageClient.fetchThumbnailUrl(
                language = card.lang,
                pageId = card.pageId,
                title = card.title,
                maxWidth = 720,
            )
        }.getOrNull()

        imageUrlMutex.withLock {
            if (fetched != null && imageUrlCache.size > 50_000) {
                val iterator = imageUrlCache.entries.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            if (fetched != null) {
                imageUrlCache[card.pageId] = fetched
            } else {
                imageUrlCache.remove(card.pageId)
            }
        }
        return fetched
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
            title = "English STEM Pack",
            subtitle = "Focused on science, technology, health, and environment topics.",
            downloadSize = "~1.2 MB (gzip)",
            installSize = "~20 MB",
            manifestUrl = "https://packs.example.invalid/packs/en-science-250k/v1/manifest.json",
            available = true,
            articleCount = 16_811,
            shardCount = 1,
            includedTopics = listOf("Science", "Technology", "Health", "Environment"),
        ),
        PackOption(
            id = "en-history-250k",
            title = "English History & Society",
            subtitle = "Focused on history, biography, culture, and politics topics.",
            downloadSize = "~16 MB (gzip)",
            installSize = "~340 MB",
            manifestUrl = "https://packs.example.invalid/packs/en-history-250k/v1/manifest.json",
            available = true,
            articleCount = 250_000,
            shardCount = 7,
            includedTopics = listOf("History", "Biography", "Culture", "Politics"),
        ),
        PackOption(
            id = "en-all-summaries",
            title = "English All Summaries",
            subtitle = "Largest available EN pack with all extracted short summaries.",
            downloadSize = "~384 MB (gzip)",
            installSize = "~6-9 GB",
            manifestUrl = "https://packs.example.invalid/packs/en-all-summaries/v1/manifest.json",
            available = true,
            articleCount = 6_262_893,
            shardCount = 157,
            includedTopics = listOf("All"),
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

private fun PackUpdateProgress.toUi(): UpdateProgressUi {
    return UpdateProgressUi(
        phase = phase,
        percent = percent,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        bytesPerSecond = bytesPerSecond,
        detail = detail,
    )
}

fun formatUpdateTimestamp(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching {
        val instant = Instant.parse(iso)
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    }.getOrElse { iso }
}
