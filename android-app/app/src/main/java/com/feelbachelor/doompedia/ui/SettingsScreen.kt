package com.feelbachelor.doompedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    settings: UserSettings,
    updateInProgress: Boolean,
    onSetPersonalization: (PersonalizationLevel) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetManifestUrl: (String) -> Unit,
    onCheckUpdatesNow: () -> Unit,
) {
    var manifestUrlDraft by remember(settings.manifestUrl) { mutableStateOf(settings.manifestUrl) }
    LaunchedEffect(settings.manifestUrl) {
        if (settings.manifestUrl != manifestUrlDraft) {
            manifestUrlDraft = settings.manifestUrl
        }
    }

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsCard(
            title = "Personalization",
            subtitle = "Transparent and not aggressive",
        ) {
            EnumOptions(
                selected = settings.personalizationLevel,
                values = PersonalizationLevel.entries,
                label = { it.name },
                onSelect = onSetPersonalization,
            )
        }

        SettingsCard(
            title = "Theme",
            subtitle = "Light, dark, or system",
        ) {
            EnumOptions(
                selected = settings.themeMode,
                values = ThemeMode.entries,
                label = { it.name },
                onSelect = onSetThemeMode,
            )
        }

        SettingsCard(
            title = "Downloads",
            subtitle = "Data saving controls",
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
                placeholder = { Text("https://example.org/manifest.json") },
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
            title = "Scope",
            subtitle = "Current language pack",
        ) {
            Text(
                text = "Language: ${settings.language}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "No account required. Preferences stay on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Wikipedia content licensed under CC BY-SA.",
                style = MaterialTheme.typography.bodySmall,
            )
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
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            TextButton(onClick = { onSelect(value) }) {
                val marker = if (value == selected) "• " else ""
                Text(text = marker + label(value))
            }
        }
    }
}
