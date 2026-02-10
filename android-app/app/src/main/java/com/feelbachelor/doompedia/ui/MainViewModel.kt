package com.feelbachelor.doompedia.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.feelbachelor.doompedia.AppContainer
import com.feelbachelor.doompedia.data.repo.UserSettings
import com.feelbachelor.doompedia.data.repo.WikiRepository
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.RankedCard
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
import kotlinx.coroutines.launch

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
                val shouldRefresh = previousSettings == null ||
                    previousSettings?.language != settings.language ||
                    previousSettings?.personalizationLevel != settings.personalizationLevel
                _uiState.update { it.copy(settings = settings) }
                previousSettings = settings
                if (shouldRefresh) {
                    refreshFeed()
                }
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
            val results = container.repository.searchByTitle(settings.language, value)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun refreshFeed() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                container.repository.loadFeed(
                    language = settings.language,
                    personalizationLevel = settings.personalizationLevel,
                )
            }.onSuccess { feed ->
                _uiState.update {
                    it.copy(
                        feed = feed,
                        loading = false,
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
                val folders = container.repository.saveFolders()
                val selected = if (folders.any { it.folderId == _uiState.value.selectedFolderId }) {
                    _uiState.value.selectedFolderId
                } else {
                    folders.firstOrNull()?.folderId ?: WikiRepository.DEFAULT_BOOKMARKS_FOLDER_ID
                }
                val cards = if (folders.isNotEmpty()) {
                    container.repository.savedCards(selected)
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
                val cards = container.repository.savedCards(folderId)
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

    fun openExternalUrl(url: String) {
        viewModelScope.launch {
            _events.emit(UiEvent.OpenUrl(url))
        }
    }

    fun setPersonalization(level: PersonalizationLevel) {
        viewModelScope.launch {
            container.preferences.setPersonalization(level)
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
            }.onFailure { error ->
                _events.emit(UiEvent.Snackbar(error.message ?: "Could not import settings JSON"))
            }
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
        }
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
