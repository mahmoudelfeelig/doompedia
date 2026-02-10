package com.feelbachelor.doompedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feelbachelor.doompedia.data.repo.UserSettings

@Composable
fun PacksScreen(
    paddingValues: PaddingValues,
    settings: UserSettings,
    updateInProgress: Boolean,
    packs: List<PackOption>,
    onChoosePack: (PackOption) -> Unit,
    onSetManifestUrl: (String) -> Unit,
    onCheckUpdatesNow: () -> Unit,
) {
    var manifestUrlDraft by remember(settings.manifestUrl) { mutableStateOf(settings.manifestUrl) }
    LaunchedEffect(settings.manifestUrl) {
        if (settings.manifestUrl != manifestUrlDraft) {
            manifestUrlDraft = settings.manifestUrl
        }
    }

    LazyColumn(
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            Text(
                text = "Choose a pack",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        items(packs, key = { it.id }) { pack ->
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = pack.title, style = MaterialTheme.typography.titleMedium)
                    Text(text = pack.subtitle, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Download: ${pack.downloadSize} | Installed: ${pack.installSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onChoosePack(pack) },
                            enabled = pack.available && pack.manifestUrl.isNotBlank(),
                        ) {
                            Text(if (pack.available) "Use pack" else "Coming soon")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Manifest URL (advanced)",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = manifestUrlDraft,
                onValueChange = {
                    manifestUrlDraft = it
                    onSetManifestUrl(it)
                },
                label = { Text("Manifest URL") },
                placeholder = { Text("https://packs.example.com/packs/en-core-1m/v1/manifest.json") },
                singleLine = true,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onCheckUpdatesNow,
                    enabled = !updateInProgress,
                ) {
                    Text(if (updateInProgress) "Checking..." else "Download / update")
                }
            }
        }

        item {
            Text(
                text = "Installed pack version: ${settings.installedPackVersion}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (settings.lastUpdateIso.isNotBlank()) {
            item {
                Text(
                    text = "Last checked at: ${settings.lastUpdateIso}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (settings.lastUpdateStatus.isNotBlank()) {
            item {
                Text(
                    text = settings.lastUpdateStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
