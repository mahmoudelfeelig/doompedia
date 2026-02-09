package com.feelbachelor.doompedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feelbachelor.doompedia.domain.ArticleCard
import com.feelbachelor.doompedia.domain.RankedCard

@Composable
fun FeedScreen(
    paddingValues: PaddingValues,
    state: MainUiState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenCard: (ArticleCard) -> Unit,
    onToggleBookmark: (ArticleCard) -> Unit,
    onLessLike: (ArticleCard) -> Unit,
) {
    val cards = if (state.query.isBlank()) {
        state.feed
    } else {
        state.searchResults.map { card -> RankedCard(card = card, score = 0.0, why = "Search match") }
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
                    onLessLike = onLessLike,
                )
            }
        }
    }
}

@Composable
private fun ArticleCardItem(
    item: RankedCard,
    onOpenCard: (ArticleCard) -> Unit,
    onToggleBookmark: (ArticleCard) -> Unit,
    onLessLike: (ArticleCard) -> Unit,
) {
    val card = item.card
    Card {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(card.topicKey) })
                if (card.bookmarked) {
                    AssistChip(onClick = {}, label = { Text("Bookmarked") })
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
            Text(
                text = item.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onOpenCard(card) }) {
                    Text("Open")
                }
                TextButton(onClick = { onToggleBookmark(card) }) {
                    Text(if (card.bookmarked) "Unsave" else "Save")
                }
                TextButton(onClick = { onLessLike(card) }) {
                    Text("Less like this")
                }
            }
        }
    }
}
