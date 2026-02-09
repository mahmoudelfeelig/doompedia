package com.feelbachelor.doompedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AttributionScreen(
    paddingValues: PaddingValues,
    onOpenExternalUrl: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Attribution",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "This app uses Wikipedia content under CC BY-SA. " +
                        "Wikipedia is a trademark of the Wikimedia Foundation. " +
                        "This app is not endorsed by or affiliated with the Wikimedia Foundation.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { onOpenExternalUrl("https://creativecommons.org/licenses/by-sa/4.0/") }) {
                    Text("Open CC BY-SA 4.0 license")
                }
                TextButton(onClick = { onOpenExternalUrl("https://en.wikipedia.org/") }) {
                    Text("Open Wikipedia source")
                }
            }
        }
    }
}
