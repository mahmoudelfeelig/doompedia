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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
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
    updateProgress: UpdateProgressUi?,
    imagePrefetch: ImagePrefetchUi,
    packs: List<PackOption>,
    onChoosePack: (PackOption) -> Unit,
    onAddPackByManifestUrl: (String) -> Unit,
    onRemovePack: (PackOption) -> Unit,
    onSetDownloadPreviewImages: (Boolean) -> Unit,
    onDownloadImagesNow: () -> Unit,
    onSetManifestUrl: (String) -> Unit,
    onCheckUpdatesNow: () -> Unit,
) {
    var manifestUrlDraft by remember(settings.manifestUrl) { mutableStateOf(settings.manifestUrl) }
    var addManifestDraft by remember { mutableStateOf("") }
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
                        text = "Articles: ${pack.articleCount} · Shards: ${pack.shardCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Download: ${pack.downloadSize} · Installed: ${pack.installSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pack.includedTopics.isNotEmpty()) {
                        Text(
                            text = "Includes: ${pack.includedTopics.joinToString(limit = 8, truncated = "…")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onChoosePack(pack) },
                            enabled = pack.available && pack.manifestUrl.isNotBlank(),
                        ) {
                            Text(if (pack.available) "Use pack" else "Coming soon")
                        }
                        if (pack.removable) {
                            Button(
                                onClick = { onRemovePack(pack) },
                                enabled = !updateInProgress,
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Add pack by manifest URL",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = addManifestDraft,
                onValueChange = { addManifestDraft = it },
                label = { Text("New pack manifest URL") },
                placeholder = { Text("https://packs.example.com/packs/<id>/v1/manifest.json") },
                singleLine = true,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        onAddPackByManifestUrl(addManifestDraft)
                        addManifestDraft = ""
                    },
                    enabled = addManifestDraft.isNotBlank() && !updateInProgress,
                ) {
                    Text("Add pack")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Download preview images",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.downloadPreviewImages,
                    onCheckedChange = onSetDownloadPreviewImages,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onDownloadImagesNow,
                    enabled = settings.downloadPreviewImages && !imagePrefetch.running,
                ) {
                    Text(if (imagePrefetch.running) "Downloading images..." else "Download images now")
                }
            }
        }

        if (imagePrefetch.running || imagePrefetch.scanned > 0) {
            item {
                val total = imagePrefetch.total.coerceAtLeast(1)
                val progress = (imagePrefetch.scanned.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Image cache progress",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${(progress * 100f).toInt()}% • scanned ${imagePrefetch.scanned}/${imagePrefetch.total} • cached ${imagePrefetch.downloaded}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = "Installed pack version: ${settings.installedPackVersion}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (updateProgress != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${updateProgress.phase} ${updateProgress.detail}".trim(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(
                        progress = { (updateProgress.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val totalLabel = if (updateProgress.totalBytes > 0L) {
                        "${formatBytes(updateProgress.downloadedBytes)} / ${formatBytes(updateProgress.totalBytes)}"
                    } else {
                        formatBytes(updateProgress.downloadedBytes)
                    }
                    val speedLabel = if (updateProgress.bytesPerSecond > 0L) {
                        " • ${formatBytes(updateProgress.bytesPerSecond)}/s"
                    } else {
                        ""
                    }
                    Text(
                        text = "${"%.1f".format(updateProgress.percent)}% • $totalLabel$speedLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (settings.lastUpdateIso.isNotBlank()) {
            item {
                Text(
                    text = "Last checked: ${formatUpdateTimestamp(settings.lastUpdateIso)}",
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
