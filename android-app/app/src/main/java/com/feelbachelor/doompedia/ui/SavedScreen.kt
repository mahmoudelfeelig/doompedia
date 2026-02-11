package com.feelbachelor.doompedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.feelbachelor.doompedia.data.repo.WikiRepository
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.ReadSort

@Composable
fun SavedScreen(
    paddingValues: PaddingValues,
    state: MainUiState,
    onRefreshSaved: () -> Unit,
    onSelectFolder: (Long) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onSetReadSort: (ReadSort) -> Unit,
    onExportSelectedFolder: () -> Unit,
    onExportAllFolders: () -> Unit,
    onImportFolders: (String) -> Unit,
    onOpenCard: (ArticleCard) -> Unit,
    onToggleBookmark: (ArticleCard) -> Unit,
    onUnsaveFromSelectedFolder: (ArticleCard) -> Unit,
) {
    var newFolderName by remember { mutableStateOf("") }
    var importDraft by remember { mutableStateOf("") }
    val selectedFolder = state.folders.firstOrNull { it.folderId == state.selectedFolderId }
    val isReadFolder = selectedFolder?.folderId == WikiRepository.DEFAULT_READ_FOLDER_ID

    LazyColumn(
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            Text(
                text = "Saved",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Folders",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("New folder") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            singleLine = true,
                        )
                        OutlinedButton(
                            onClick = {
                                onCreateFolder(newFolderName)
                                newFolderName = ""
                            },
                            enabled = newFolderName.isNotBlank(),
                        ) {
                            Text("Add")
                        }
                    }
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.folders.forEach { folder ->
                            AssistChip(
                                onClick = { onSelectFolder(folder.folderId) },
                                label = {
                                    val marker = if (folder.folderId == state.selectedFolderId) "• " else ""
                                    Text("$marker${folder.name} (${folder.articleCount})")
                                },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRefreshSaved) { Text("Refresh") }
                        OutlinedButton(onClick = onExportSelectedFolder) { Text("Export selected") }
                        OutlinedButton(onClick = onExportAllFolders) { Text("Export all") }
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = importDraft,
                        onValueChange = { importDraft = it },
                        label = { Text("Import folders JSON") },
                        placeholder = { Text("{\"folders\":[...]}") },
                    )
                    OutlinedButton(
                        onClick = {
                            onImportFolders(importDraft)
                            importDraft = ""
                        },
                        enabled = importDraft.isNotBlank(),
                    ) {
                        Text("Import folders")
                    }
                }
            }
        }

        if (isReadFolder) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onSetReadSort(ReadSort.NEWEST_FIRST) },
                        label = { Text(if (state.settings.readSort == ReadSort.NEWEST_FIRST) "• Latest" else "Latest") },
                    )
                    AssistChip(
                        onClick = { onSetReadSort(ReadSort.OLDEST_FIRST) },
                        label = { Text(if (state.settings.readSort == ReadSort.OLDEST_FIRST) "• Earliest" else "Earliest") },
                    )
                }
            }
        }

        selectedFolder?.takeIf { !it.isDefault }?.let { folder ->
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDeleteFolder(folder.folderId) }) {
                        Text("Remove folder")
                    }
                }
            }
        }

        item {
            Text(
                text = selectedFolder?.name ?: "Saved articles",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (state.savedCards.isEmpty()) {
            item {
                Text(
                    text = if (isReadFolder) {
                        "Read activity is empty. Open some cards from Feed first."
                    } else {
                        "No saved articles in this folder yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.savedCards, key = { it.pageId }) { card ->
            Card(
                modifier = Modifier.clickable { onOpenCard(card) },
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = card.summary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!isReadFolder) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (selectedFolder?.folderId == WikiRepository.DEFAULT_BOOKMARKS_FOLDER_ID) {
                                        onToggleBookmark(card)
                                    } else {
                                        onUnsaveFromSelectedFolder(card)
                                    }
                                }
                            ) {
                                Text(
                                    if (selectedFolder?.folderId == WikiRepository.DEFAULT_BOOKMARKS_FOLDER_ID) {
                                        if (card.bookmarked) "Unsave" else "Save"
                                    } else {
                                        "Unsave"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
