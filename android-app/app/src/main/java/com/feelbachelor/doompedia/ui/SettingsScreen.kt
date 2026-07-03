package com.feelbachelor.doompedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feelbachelor.doompedia.data.repo.UserSettings
import com.feelbachelor.doompedia.domain.FeedMode
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.ThemeMode

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    settings: UserSettings,
    effectiveFeedMode: FeedMode,
    onSetFeedMode: (FeedMode) -> Unit,
    onSetPersonalization: (PersonalizationLevel) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onSetHighContrast: (Boolean) -> Unit,
    onSetReduceMotion: (Boolean) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
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
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Keep Doompedia focused, readable, and offline-friendly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsCard(title = "Reading") {
                SettingRow(
                    title = "Feed source",
                    supporting = if (effectiveFeedMode != settings.feedMode) {
                        "Offline is active until the connection returns."
                    } else {
                        null
                    },
                ) {
                    CompactChoice(
                        selected = settings.feedMode,
                        values = FeedMode.entries,
                        label = {
                            when (it) {
                                FeedMode.OFFLINE -> "Offline"
                                FeedMode.ONLINE -> "Live"
                            }
                        },
                        onSelect = onSetFeedMode,
                    )
                }

                SettingRow(title = "Personalization") {
                    CompactChoice(
                        selected = settings.personalizationLevel,
                        values = PersonalizationLevel.entries,
                        label = {
                            when (it) {
                                PersonalizationLevel.OFF -> "Off"
                                PersonalizationLevel.LOW -> "Low"
                                PersonalizationLevel.MEDIUM -> "Med"
                                PersonalizationLevel.HIGH -> "High"
                            }
                        },
                        onSelect = onSetPersonalization,
                    )
                }
            }
        }

        item {
            SettingsCard(title = "Appearance") {
                SettingRow(title = "Theme") {
                    CompactChoice(
                        selected = settings.themeMode,
                        values = ThemeMode.entries,
                        label = {
                            when (it) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            }
                        },
                        onSelect = onSetThemeMode,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Text size", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${(settings.fontScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = settings.fontScale,
                        onValueChange = onSetFontScale,
                        valueRange = 0.9f..1.25f,
                        steps = 6,
                    )
                }

                SettingRow(title = "Reduce motion") {
                    Switch(
                        checked = settings.reduceMotion,
                        onCheckedChange = onSetReduceMotion,
                    )
                }

                SettingRow(title = "High contrast") {
                    Switch(
                        checked = settings.highContrast,
                        onCheckedChange = onSetHighContrast,
                    )
                }
            }
        }

        item {
            SettingsCard(title = "Downloads") {
                SettingRow(
                    title = "Wi-Fi only",
                    supporting = "Used for background pack and image downloads.",
                ) {
                    Switch(
                        checked = settings.wifiOnlyDownloads,
                        onCheckedChange = onSetWifiOnly,
                    )
                }
            }
        }

        item {
            SettingsCard(title = "About") {
                Text(
                    text = "Doompedia uses Wikipedia content and Wikimedia-hosted article pages. It is not affiliated with the Wikimedia Foundation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onOpenExternalUrl("https://creativecommons.org/licenses/by-sa/4.0/") }) {
                    Text("CC BY-SA license")
                }
                TextButton(onClick = { onOpenExternalUrl("https://www.wikipedia.org/") }) {
                    Text("Wikipedia")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    supporting: String? = null,
    control: @Composable () -> Unit,
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
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        control()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> CompactChoice(
    selected: T,
    values: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
            )
        }
    }
}
