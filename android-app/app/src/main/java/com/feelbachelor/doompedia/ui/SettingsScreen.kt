package com.feelbachelor.doompedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feelbachelor.doompedia.data.repo.UserSettings
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.ThemeMode
import com.feelbachelor.doompedia.ui.theme.parseHexColor

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    settings: UserSettings,
    updateInProgress: Boolean,
    onSetPersonalization: (PersonalizationLevel) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentHex: (String) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetManifestUrl: (String) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    var manifestUrlDraft by remember(settings.manifestUrl) { mutableStateOf(settings.manifestUrl) }
    var accentHexDraft by remember(settings.accentHex) { mutableStateOf(settings.accentHex) }
    var importDraft by remember { mutableStateOf("") }
    LaunchedEffect(settings.manifestUrl) {
        if (settings.manifestUrl != manifestUrlDraft) {
            manifestUrlDraft = settings.manifestUrl
        }
    }
    LaunchedEffect(settings.accentHex) {
        if (settings.accentHex != accentHexDraft) {
            accentHexDraft = settings.accentHex
        }
    }

    val presets = listOf(
        "#0B6E5B",
        "#1363DF",
        "#C44536",
        "#F59E0B",
        "#7E22CE",
        "#1F2937",
    )

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsCard(
            title = "Personalization",
            subtitle = "Choose how strongly your reading behavior influences ranking",
        ) {
            EnumOptions(
                selected = settings.personalizationLevel,
                values = PersonalizationLevel.entries,
                label = { it.name },
                onSelect = onSetPersonalization,
            )
            Text(
                text = when (settings.personalizationLevel) {
                    PersonalizationLevel.OFF -> "OFF: No behavior-based tuning. Feed stays mostly neutral."
                    PersonalizationLevel.LOW -> "LOW: Light personalization with strong diversity guardrails."
                    PersonalizationLevel.MEDIUM -> "MEDIUM: Balanced personalization and exploration."
                    PersonalizationLevel.HIGH -> "HIGH: Stronger adaptation while still capped by anti-bubble limits."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SettingsCard(
            title = "Appearance",
            subtitle = "Theme mode and custom accent color",
        ) {
            EnumOptions(
                selected = settings.themeMode,
                values = ThemeMode.entries,
                label = { it.name },
                onSelect = onSetThemeMode,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(
                                color = parseHexColor(hex, MaterialTheme.colorScheme.primary),
                                shape = CircleShape,
                            )
                            .clickable {
                                accentHexDraft = hex
                                onSetAccentHex(hex)
                            },
                    )
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = accentHexDraft,
                onValueChange = {
                    accentHexDraft = it
                    onSetAccentHex(it)
                },
                label = { Text("Accent hex color") },
                placeholder = { Text("#0B6E5B") },
                singleLine = true,
            )
        }

        SettingsCard(
            title = "Data and updates",
            subtitle = "Manual update checks only",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Wi-Fi only",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = settings.wifiOnlyDownloads,
                    onCheckedChange = onSetWifiOnly,
                )
            }
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
            Button(
                onClick = onCheckUpdatesNow,
                enabled = !updateInProgress,
            ) {
                Text(if (updateInProgress) "Checking..." else "Check updates now")
            }
            Text(
                text = "Installed pack version: ${settings.installedPackVersion}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (settings.lastUpdateIso.isNotBlank()) {
                Text(
                    text = "Last check: ${settings.lastUpdateIso}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (settings.lastUpdateStatus.isNotBlank()) {
                Text(
                    text = settings.lastUpdateStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        SettingsCard(
            title = "Settings backup",
            subtitle = "Export or import settings JSON",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onExportSettings) {
                    Text("Export (copy)")
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = importDraft,
                onValueChange = { importDraft = it },
                label = { Text("Import JSON") },
                placeholder = { Text("{\"themeMode\":\"DARK\", ...}") },
            )
            Button(
                onClick = { onImportSettings(importDraft) },
                enabled = importDraft.isNotBlank(),
            ) {
                Text("Import settings")
            }
        }

        SettingsCard(
            title = "Attribution",
            subtitle = "Wikipedia content and licensing",
        ) {
            Text(
                text = "This app uses Wikipedia content and summaries under CC BY-SA 4.0.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { onOpenExternalUrl("https://creativecommons.org/licenses/by-sa/4.0/") }) {
                Text("Open CC BY-SA 4.0")
            }
            TextButton(onClick = { onOpenExternalUrl("https://www.wikipedia.org/") }) {
                Text("Open Wikipedia")
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
private fun <T : Enum<T>> EnumOptions(
    selected: T,
    values: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            TextButton(onClick = { onSelect(value) }) {
                val marker = if (value == selected) "• " else ""
                Text(text = marker + label(value))
            }
        }
    }
}
