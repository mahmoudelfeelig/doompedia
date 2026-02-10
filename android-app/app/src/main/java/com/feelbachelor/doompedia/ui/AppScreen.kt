package com.feelbachelor.doompedia.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.FeedMode
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.ReadSort
import com.feelbachelor.doompedia.domain.ThemeMode

private enum class Tab(val title: String, val icon: ImageVector) {
    FEED("Explore", Icons.AutoMirrored.Outlined.Article),
    SAVED("Saved", Icons.Outlined.Bookmark),
    PACKS("Packs", Icons.Outlined.CloudDownload),
    SETTINGS("Settings", Icons.Outlined.Settings),
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
    onSetReadSort: (ReadSort) -> Unit,
    onExportSelectedFolder: () -> Unit,
    onExportAllFolders: () -> Unit,
    onImportFolders: (String) -> Unit,
    onChoosePack: (PackOption) -> Unit,
    onAddPackByManifestUrl: (String) -> Unit,
    onRemovePack: (PackOption) -> Unit,
    onSetFeedMode: (FeedMode) -> Unit,
    onSetPersonalization: (PersonalizationLevel) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentHex: (String) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onSetHighContrast: (Boolean) -> Unit,
    onSetReduceMotion: (Boolean) -> Unit,
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
                        onClick = {
                            if (selectedTab == index && tab == Tab.FEED) {
                                onRefresh()
                            }
                            selectedTab = index
                        },
                        label = { Text(tab.title) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                            )
                        },
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
                onSetReadSort = onSetReadSort,
                onExportSelectedFolder = onExportSelectedFolder,
                onExportAllFolders = onExportAllFolders,
                onImportFolders = onImportFolders,
                onOpenCard = onOpenCard,
                onToggleBookmark = onToggleBookmark,
                onShowFolderPicker = onShowFolderPicker,
            )

            Tab.PACKS -> PacksScreen(
                paddingValues = padding,
                settings = state.settings,
                updateInProgress = state.updateInProgress,
                packs = state.packCatalog,
                onChoosePack = onChoosePack,
                onAddPackByManifestUrl = onAddPackByManifestUrl,
                onRemovePack = onRemovePack,
                onSetManifestUrl = onSetManifestUrl,
                onCheckUpdatesNow = onCheckUpdatesNow,
            )

            Tab.SETTINGS -> SettingsScreen(
                paddingValues = padding,
                settings = state.settings,
                effectiveFeedMode = state.effectiveFeedMode,
                onSetFeedMode = onSetFeedMode,
                onSetPersonalization = onSetPersonalization,
                onSetThemeMode = onSetThemeMode,
                onSetAccentHex = onSetAccentHex,
                onSetFontScale = onSetFontScale,
                onSetHighContrast = onSetHighContrast,
                onSetReduceMotion = onSetReduceMotion,
                onSetWifiOnly = onSetWifiOnly,
                onExportSettings = onExportSettings,
                onImportSettings = onImportSettings,
                onOpenExternalUrl = onOpenExternalUrl,
            )
        }
    }
}
