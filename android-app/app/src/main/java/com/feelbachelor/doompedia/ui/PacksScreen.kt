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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
    val activePack = packs.firstOrNull {
        it.manifestUrl.equals(settings.manifestUrl, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Packs",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Download article cards once, then explore and search offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Active pack", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = activePack?.title ?: "No hosted pack selected",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = if (settings.installedPackVersion > 0) {
                            "Installed version ${settings.installedPackVersion}"
                        } else {
                            "Choose a pack below, then download it."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onCheckUpdatesNow,
                        enabled = !updateInProgress && activePack != null,
                    ) {
                        Text(if (updateInProgress) "Working..." else "Download / update")
                    }
                }
            }
        }

        if (updateProgress != null) {
            item {
                ProgressCard(updateProgress = updateProgress)
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Article images", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Load Wikipedia thumbnails when available. Cached images use up to 512 MB.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.downloadPreviewImages,
                            onCheckedChange = onSetDownloadPreviewImages,
                        )
                    }
                    Button(
                        onClick = onDownloadImagesNow,
                        enabled = settings.downloadPreviewImages && !imagePrefetch.running,
                    ) {
                        Text(if (imagePrefetch.running) "Caching..." else "Cache images")
                    }
                    if (imagePrefetch.running || imagePrefetch.scanned > 0) {
                        val total = imagePrefetch.total.coerceAtLeast(1)
                        val progress = (imagePrefetch.scanned.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${(progress * 100f).toInt()}% scanned, ${imagePrefetch.downloaded} cached",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(packs.filter { !it.removable }, key = { it.id }) { pack ->
            PackCard(
                pack = pack,
                selected = pack.id == activePack?.id,
                updateInProgress = updateInProgress,
                onChoosePack = onChoosePack,
            )
        }

        if (settings.lastUpdateIso.isNotBlank() || settings.lastUpdateStatus.isNotBlank()) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Last update", style = MaterialTheme.typography.titleMedium)
                        if (settings.lastUpdateIso.isNotBlank()) {
                            Text(
                                text = formatUpdateTimestamp(settings.lastUpdateIso),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (settings.lastUpdateStatus.isNotBlank()) {
                            Text(
                                text = settings.lastUpdateStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackCard(
    pack: PackOption,
    selected: Boolean,
    updateInProgress: Boolean,
    onChoosePack: (PackOption) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = pack.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pack.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${pack.articleCount} cards • ${pack.shardCount} shards • ${pack.downloadSize}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onChoosePack(pack) },
                enabled = pack.available && !updateInProgress && !selected,
            ) {
                Text(
                    when {
                        selected -> "Selected"
                        pack.available -> "Use this pack"
                        else -> "Coming soon"
                    }
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(updateProgress: UpdateProgressUi) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${updateProgress.phase} ${updateProgress.detail}".trim(),
                style = MaterialTheme.typography.titleMedium,
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
            Text(
                text = "${"%.1f".format(updateProgress.percent)}% • $totalLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
