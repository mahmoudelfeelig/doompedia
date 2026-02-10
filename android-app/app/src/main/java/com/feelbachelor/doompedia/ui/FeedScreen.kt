package com.feelbachelor.doompedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.RankedCard
import kotlin.math.ceil

@Composable
fun FeedScreen(
    paddingValues: PaddingValues,
    state: MainUiState,
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
) {
    val cards = if (state.query.isBlank()) {
        state.feed
    } else {
        state.searchResults.map { card -> RankedCard(card = card, score = 0.0, why = "Search match by title or alias") }
    }

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search title") },
            placeholder = { Text("Try: Ada Lovelace") },
            singleLine = true,
        )

        if (state.loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                TextButton(onClick = onRefresh) {
                    Text("Refresh feed")
                }
            }
            items(cards, key = { it.card.pageId }) { item ->
                ArticleCardItem(
                    item = item,
                    onOpenCard = onOpenCard,
                    onToggleBookmark = onToggleBookmark,
                    onMoreLike = onMoreLike,
                    onLessLike = onLessLike,
                    onShowFolderPicker = onShowFolderPicker,
                )
            }
        }
    }

    state.folderPicker?.let { picker ->
        AlertDialog(
            onDismissRequest = onDismissFolderPicker,
            title = { Text("Save \"${picker.card.title}\" to folders") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.folders.isEmpty()) {
                        Text("No folders yet. Create one in Saved tab.")
                    } else {
                        state.folders.forEach { folder ->
                            val checked = folder.folderId in picker.selectedFolderIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleFolderSelection(folder.folderId) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${folder.name} (${folder.articleCount})",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onToggleFolderSelection(folder.folderId) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onApplyFolderSelection) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissFolderPicker) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ArticleCardItem(
    item: RankedCard,
    onOpenCard: (ArticleCard) -> Unit,
    onToggleBookmark: (ArticleCard) -> Unit,
    onMoreLike: (ArticleCard) -> Unit,
    onLessLike: (ArticleCard) -> Unit,
    onShowFolderPicker: (ArticleCard) -> Unit,
) {
    val card = item.card
    val showWhy = remember(card.pageId) { mutableStateOf(false) }

    Card(
        modifier = Modifier.clickable { onOpenCard(card) },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    buildTags(card).forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag) })
                    }
                }
                TextButton(onClick = { showWhy.value = true }) {
                    Text("i")
                }
            }

            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onToggleBookmark(card) }) {
                    Text(if (card.bookmarked) "Unsave" else "Save")
                }
                OutlinedButton(onClick = { onMoreLike(card) }) {
                    Text("Show more")
                }
                OutlinedButton(onClick = { onLessLike(card) }) {
                    Text("Show less")
                }
                OutlinedButton(onClick = { onShowFolderPicker(card) }) {
                    Text("Folders")
                }
            }
        }
    }

    if (showWhy.value) {
        AlertDialog(
            onDismissRequest = { showWhy.value = false },
            title = { Text("Why this is shown") },
            text = {
                Text(
                    "This recommendation is based on your recent reading behavior, "
                        + "topic balancing, and exploration rules.\n\n${item.why}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showWhy.value = false }) {
                    Text("Got it")
                }
            },
        )
    }
}

private fun buildTags(card: ArticleCard): List<String> {
    val words = card.summary.split("\\s+".toRegex()).filter { it.isNotBlank() }
    val readMinutes = ceil(words.size / 220.0).toInt().coerceAtLeast(1)
    val updatedYear = card.updatedAt.take(4).takeIf { it.all(Char::isDigit) }
    val qualityTag = when {
        card.qualityScore >= 0.85 -> "High quality"
        card.qualityScore >= 0.65 -> "Solid quality"
        else -> "Fresh pick"
    }
    val tags = mutableListOf<String>()
    tags += prettyTopic(card.topicKey)
    tags += card.lang.uppercase()
    tags += "${readMinutes}m read"
    tags += qualityTag
    updatedYear?.let { tags += it }
    if (card.bookmarked) tags += "Bookmarked"
    if (card.isDisambiguation) tags += "Disambiguation"
    return tags.take(6)
}

private fun prettyTopic(raw: String): String {
    return raw
        .replace('-', ' ')
        .replace('_', ' ')
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.lowercase().replaceFirstChar { c -> c.titlecase() }
        }
        .ifBlank { "General" }
}
