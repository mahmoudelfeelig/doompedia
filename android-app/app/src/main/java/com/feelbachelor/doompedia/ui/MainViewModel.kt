package com.feelbachelor.doompedia.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.feelbachelor.doompedia.AppContainer
import com.feelbachelor.doompedia.data.repo.UserSettings
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.RankedCard
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

data class MainUiState(
    val settings: UserSettings = UserSettings(),
    val query: String = "",
    val loading: Boolean = true,
    val updateInProgress: Boolean = false,
    val feed: List<RankedCard> = emptyList(),
    val searchResults: List<ArticleCard> = emptyList(),
    val error: String? = null,
)

sealed interface UiEvent {
    data class OpenUrl(val url: String) : UiEvent
    data class Snackbar(val message: String) : UiEvent
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
            runCatching { container.bootstrapper.ensureSeedData() }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
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

    fun onOpenCard(card: ArticleCard) {
        viewModelScope.launch {
            container.repository.recordOpen(card, _uiState.value.settings.personalizationLevel)
            _events.emit(UiEvent.OpenUrl(card.wikiUrl))
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
            val message = if (bookmarked) "Saved bookmark" else "Removed bookmark"
            _events.emit(UiEvent.Snackbar(message))
            refreshFeed()
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
