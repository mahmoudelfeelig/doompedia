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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ThumbDownOffAlt
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.feelbachelor.doompedia.data.repo.WikiRepository
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.CardKeywords
import com.feelbachelor.doompedia.domain.RankedCard
import kotlinx.coroutines.launch

enum class FeedSortOption(val label: String) {
    RELEVANCE("Recommended"),
    TITLE_ASC("A-Z"),
    TITLE_DESC("Z-A"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedScreen(
    paddingValues: PaddingValues,
    state: MainUiState,
    listState: LazyListState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    exploreReselectToken: Long,
    onOpenCard: (ArticleCard) -> Unit,
    onMoreLike: (ArticleCard) -> Unit,
    onLessLike: (ArticleCard) -> Unit,
    onShowFolderPicker: (ArticleCard) -> Unit,
    onToggleFolderSelection: (Long) -> Unit,
    onApplyFolderSelection: () -> Unit,
    onDismissFolderPicker: () -> Unit,
    onResolveThumbnailUrl: suspend (ArticleCard) -> String?,
    downloadPreviewImages: Boolean,
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
    var pendingScrollToTopAfterRefresh by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun requestRefreshAndResetTop() {
        pendingScrollToTopAfterRefresh = true
        onRefresh()
    }

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

    LaunchedEffect(exploreReselectToken) {
        if (exploreReselectToken <= 0L) return@LaunchedEffect
        val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        if (atTop) {
            requestRefreshAndResetTop()
        } else {
            scope.launch { listState.animateScrollToItem(0) }
        }
    }

    LaunchedEffect(state.loading, pendingScrollToTopAfterRefresh) {
        if (!state.loading && pendingScrollToTopAfterRefresh) {
            listState.scrollToItem(0)
            pendingScrollToTopAfterRefresh = false
        }
    }

    LaunchedEffect(
        listState,
        activeCards.size,
        state.query,
        state.loading,
        state.loadingMoreFeed,
        state.feedHasMore,
    ) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisibleIndex ->
            val shouldLoadMore = state.query.isBlank() &&
                !state.loading &&
                !state.loadingMoreFeed &&
                state.feedHasMore &&
                activeCards.isNotEmpty() &&
                lastVisibleIndex >= activeCards.lastIndex - 6

            if (shouldLoadMore) {
                onLoadMore()
            }
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.loading,
        onRefresh = ::requestRefreshAndResetTop,
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
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                items(activeCards, key = { it.card.pageId }) { item ->
                    ArticleCardItem(
                        item = item,
                        onOpenCard = onOpenCard,
                        onMoreLike = onMoreLike,
                        onLessLike = onLessLike,
                        onShowFolderPicker = onShowFolderPicker,
                        onResolveThumbnailUrl = onResolveThumbnailUrl,
                        downloadPreviewImages = downloadPreviewImages,
                    )
                }
                if (state.loadingMoreFeed) {
                    item(key = "feed_loading_more") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
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
    onMoreLike: (ArticleCard) -> Unit,
    onLessLike: (ArticleCard) -> Unit,
    onShowFolderPicker: (ArticleCard) -> Unit,
    onResolveThumbnailUrl: suspend (ArticleCard) -> String?,
    downloadPreviewImages: Boolean,
) {
    val card = item.card
    val showWhy = remember(card.pageId) { mutableStateOf(false) }
    var thumbnailUrl by remember(card.pageId) { mutableStateOf<String?>(null) }

    LaunchedEffect(card.pageId, downloadPreviewImages) {
        if (downloadPreviewImages && isImageCandidate(card.pageId)) {
            thumbnailUrl = onResolveThumbnailUrl(card)
        } else {
            thumbnailUrl = null
        }
    }

    Card(
        modifier = Modifier.clickable { onOpenCard(card) },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "Article preview image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
            }

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
                IconButton(onClick = { onShowFolderPicker(card) }) {
                    Icon(
                        imageVector = if (card.bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (card.bookmarked) "Saved to folders" else "Save to folders",
                    )
                }
                IconButton(onClick = { onMoreLike(card) }) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbUpOffAlt,
                        contentDescription = "Like this type of article",
                    )
                }
                IconButton(onClick = { onLessLike(card) }) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbDownOffAlt,
                        contentDescription = "Dislike this type of article",
                    )
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
    val tags = linkedSetOf<String>()
    tags += CardKeywords.displayTags(
        title = card.title,
        summary = card.summary,
        topicKey = card.topicKey,
        bookmarked = card.bookmarked,
        maxTags = 6,
    )

    if (card.updatedAt.startsWith("1970-")) {
        tags += "Offline Pack"
    } else {
        tags += "Live Cache"
    }

    return tags.take(6).toList()
}

private fun isImageCandidate(pageId: Long): Boolean {
    return true
}
