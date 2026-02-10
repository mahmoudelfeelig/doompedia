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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.feelbachelor.doompedia.domain.ArticleCard

@Composable
fun SavedScreen(
    paddingValues: PaddingValues,
    state: MainUiState,
    onRefreshSaved: () -> Unit,
    onSelectFolder: (Long) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onOpenCard: (ArticleCard) -> Unit,
    onToggleBookmark: (ArticleCard) -> Unit,
    onShowFolderPicker: (ArticleCard) -> Unit,
) {
    var newFolderName by remember { mutableStateOf("") }
    val selectedFolder = state.folders.firstOrNull { it.folderId == state.selectedFolderId }

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            ) {
                Text("Add")
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
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

        selectedFolder?.takeIf { !it.isDefault }?.let { folder ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onDeleteFolder(folder.folderId) }) {
                    Text("Remove folder")
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = selectedFolder?.name ?: "Saved",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onRefreshSaved) { Text("Refresh") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.savedCards.isEmpty()) {
                item {
                    Text(
                        text = "No saved articles in this folder yet.",
                        style = MaterialTheme.typography.bodyMedium,
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = card.summary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (selectedFolder?.isDefault == true) {
                                OutlinedButton(onClick = { onToggleBookmark(card) }) {
                                    Text(if (card.bookmarked) "Unsave" else "Save")
                                }
                            }
                            OutlinedButton(onClick = { onShowFolderPicker(card) }) {
                                Text("Folders")
                            }
                        }
                    }
                }
            }
        }
    }
}
