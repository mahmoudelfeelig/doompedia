package com.feelbachelor.doompedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feelbachelor.doompedia.data.repo.WikiRepository
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.RankedCard
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

enum class FeedSortOption(val label: String) {
    RELEVANCE("Recommended"),
    TITLE_ASC("A-Z"),
    TITLE_DESC("Z-A"),
    QUALITY("Quality"),
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalLayoutApi::class)
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
    var selectedSort by rememberSaveable { mutableStateOf(FeedSortOption.RELEVANCE) }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var showFilterMenu by rememberSaveable { mutableStateOf(false) }
    var requestedBootstrapRefresh by rememberSaveable { mutableStateOf(false) }

    val availableFilters = remember(cards) {
        cards
            .flatMap { ranked -> buildTopicKeywords(ranked.card).filterNot { it.equals("Saved", ignoreCase = true) } }
            .distinct()
            .sorted()
            .take(14)
    }
    val activeCards = remember(cards, selectedSort, selectedFilter) {
        val filtered = if (selectedFilter == "All") {
            cards
        } else {
            cards.filter { ranked ->
                buildTopicKeywords(ranked.card).any { it.equals(selectedFilter, ignoreCase = true) }
            }
        }
        when (selectedSort) {
            FeedSortOption.RELEVANCE -> filtered
            FeedSortOption.TITLE_ASC -> filtered.sortedBy { it.card.title.lowercase() }
            FeedSortOption.TITLE_DESC -> filtered.sortedByDescending { it.card.title.lowercase() }
            FeedSortOption.QUALITY -> filtered.sortedByDescending { it.card.qualityScore }
        }
    }

    LaunchedEffect(availableFilters, selectedFilter) {
        if (selectedFilter != "All" && selectedFilter !in availableFilters) {
            selectedFilter = "All"
        }
    }

    LaunchedEffect(state.loading, state.query, cards.size) {
        if (!requestedBootstrapRefresh && !state.loading && state.query.isBlank() && cards.isEmpty()) {
            requestedBootstrapRefresh = true
            onRefresh()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.loading,
        onRefresh = onRefresh,
    )

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
            placeholder = { Text("Try: Alan Turing") },
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                OutlinedButton(onClick = { showSortMenu = true }) {
                    Text("Sort: ${selectedSort.label}")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    FeedSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                selectedSort = option
                                showSortMenu = false
                            },
                        )
                    }
                }
            }

            Box {
                OutlinedButton(
                    onClick = { showFilterMenu = true },
                    enabled = availableFilters.isNotEmpty(),
                ) {
                    Text(
                        if (selectedFilter == "All") {
                            "Filter: All"
                        } else {
                            "Filter: $selectedFilter"
                        },
                    )
                }
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("All") },
                        onClick = {
                            selectedFilter = "All"
                            showFilterMenu = false
                        },
                    )
                    availableFilters.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter) },
                            onClick = {
                                selectedFilter = filter
                                showFilterMenu = false
                            },
                        )
                    }
                }
            }
        }

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                items(activeCards, key = { it.card.pageId }) { item ->
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
            PullRefreshIndicator(
                refreshing = state.loading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    state.folderPicker?.let { picker ->
        AlertDialog(
            onDismissRequest = onDismissFolderPicker,
            title = {
                Text(
                    text = "Save to folders",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = picker.card.title,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Choose one or more folders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            state.folders.filter { it.folderId != WikiRepository.DEFAULT_READ_FOLDER_ID },
                            key = { it.folderId },
                        ) { folder ->
                            val checked = folder.folderId in picker.selectedFolderIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleFolderSelection(folder.folderId) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
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
                    Text("Done")
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

@OptIn(ExperimentalLayoutApi::class)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = card.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { showWhy.value = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Why this is shown",
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                buildTopicKeywords(card).forEach { tag ->
                    AssistChip(
                        onClick = {},
                        label = { Text(tag) },
                    )
                }
            }

            Text(
                text = card.summary,
                style = MaterialTheme.typography.bodyLarge,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { onShowFolderPicker(card) }) {
                    Text(if (card.bookmarked) "Saved" else "Save")
                }
                OutlinedButton(onClick = { onMoreLike(card) }) {
                    Text("Show more")
                }
                OutlinedButton(onClick = { onLessLike(card) }) {
                    Text("Show less")
                }
                if (card.bookmarked) {
                    OutlinedButton(onClick = { onToggleBookmark(card) }) {
                        Text("Unsave")
                    }
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
                    "This card is selected using your personalization level, "
                        + "topic diversity guardrails, and controlled exploration.\n\n${item.why}",
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

private fun buildTopicKeywords(card: ArticleCard): List<String> {
    val text = "${card.title} ${card.summary}".lowercase()
    val tags = linkedSetOf<String>()
    tags += prettyTopic(card.topicKey)

    if (card.updatedAt.startsWith("1970-")) {
        tags += "Offline Pack"
    } else {
        tags += "Live Cache"
    }

    keywordBuckets.forEach { (topic, keywords) ->
        if (keywords.any { keyword -> text.contains(keyword) }) {
            tags += prettyTopic(topic)
        }
    }

    if (card.title.contains("list of", ignoreCase = true)) tags += "Lists"
    if (card.title.contains("university", ignoreCase = true)) tags += "Education"
    if (card.title.contains("city", ignoreCase = true)) tags += "Places"
    if (card.title.contains("war", ignoreCase = true)) tags += "Conflict"
    if (card.bookmarked) tags += "Saved"

    return tags.take(6).toList()
}

private val keywordBuckets: List<Pair<String, List<String>>> = listOf(
    "biography" to listOf("born", "died", "biography", "person", "scientist", "author", "actor"),
    "science" to listOf("physics", "chemistry", "biology", "mathematics", "scientist", "theory"),
    "technology" to listOf("computer", "software", "algorithm", "internet", "digital", "engineering"),
    "history" to listOf("war", "empire", "century", "historical", "revolution", "dynasty"),
    "politics" to listOf("government", "election", "parliament", "policy", "minister", "president"),
    "culture" to listOf("music", "film", "art", "literature", "religion", "language"),
    "geography" to listOf("city", "country", "river", "mountain", "region", "capital"),
    "economics" to listOf("economy", "finance", "trade", "market", "industry", "currency"),
    "sports" to listOf("football", "basketball", "olympic", "athlete", "league", "championship"),
    "education" to listOf("university", "school", "college", "academy", "curriculum", "education"),
    "law" to listOf("law", "court", "judge", "legal", "constitution", "act"),
    "philosophy" to listOf("philosophy", "philosopher", "ethics", "logic", "metaphysics"),
    "art" to listOf("painting", "sculpture", "artist", "gallery", "visual art"),
    "health" to listOf("medicine", "disease", "medical", "hospital", "health", "symptom"),
)

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
