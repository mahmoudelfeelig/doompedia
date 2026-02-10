package com.feelbachelor.doompedia.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.ThemeMode

private enum class Tab(val title: String) {
    FEED("Feed"),
    SAVED("Saved"),
    SETTINGS("Settings"),
}

@Composable
fun AppScreen(
    state: MainUiState,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenCard: (ArticleCard) -> Unit,
    onToggleBookmark: (ArticleCard) -> Unit,
    onMoreLike: (ArticleCard) -> Unit,
    onLessLike: (ArticleCard) -> Unit,
    onShowFolderPicker: (ArticleCard) -> Unit,
    onToggleFolderSelection: (Long) -> Unit,
    onApplyFolderSelection: () -> Unit,
    onDismissFolderPicker: () -> Unit,
    onRefreshSaved: () -> Unit,
    onSelectSavedFolder: (Long) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onSetPersonalization: (PersonalizationLevel) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentHex: (String) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetManifestUrl: (String) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(tab.title) },
                        icon = {},
                    )
                }
            }
        },
    ) { padding ->
        when (Tab.entries[selectedTab]) {
            Tab.FEED -> FeedScreen(
                paddingValues = padding,
                state = state,
                onQueryChange = onQueryChange,
                onRefresh = onRefresh,
                onOpenCard = onOpenCard,
                onToggleBookmark = onToggleBookmark,
                onMoreLike = onMoreLike,
                onLessLike = onLessLike,
                onShowFolderPicker = onShowFolderPicker,
                onToggleFolderSelection = onToggleFolderSelection,
                onApplyFolderSelection = onApplyFolderSelection,
                onDismissFolderPicker = onDismissFolderPicker,
            )

            Tab.SAVED -> SavedScreen(
                paddingValues = padding,
                state = state,
                onRefreshSaved = onRefreshSaved,
                onSelectFolder = onSelectSavedFolder,
                onCreateFolder = onCreateFolder,
                onDeleteFolder = onDeleteFolder,
                onOpenCard = onOpenCard,
                onToggleBookmark = onToggleBookmark,
                onShowFolderPicker = onShowFolderPicker,
            )

            Tab.SETTINGS -> SettingsScreen(
                paddingValues = padding,
                settings = state.settings,
                updateInProgress = state.updateInProgress,
                onSetPersonalization = onSetPersonalization,
                onSetThemeMode = onSetThemeMode,
                onSetAccentHex = onSetAccentHex,
                onSetWifiOnly = onSetWifiOnly,
                onSetManifestUrl = onSetManifestUrl,
                onCheckUpdatesNow = onCheckUpdatesNow,
                onExportSettings = onExportSettings,
                onImportSettings = onImportSettings,
                onOpenExternalUrl = onOpenExternalUrl,
            )
        }
    }
}
