package com.feelbachelor.doompedia.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.feelbachelor.doompedia.data.repo.UserSettings
import com.feelbachelor.doompedia.domain.PersonalizationLevel
import com.feelbachelor.doompedia.domain.ThemeMode
import com.feelbachelor.doompedia.ui.theme.parseHexColor
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    settings: UserSettings,
    onSetPersonalization: (PersonalizationLevel) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentHex: (String) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onSetHighContrast: (Boolean) -> Unit,
    onSetReduceMotion: (Boolean) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    var accentHexDraft by remember(settings.accentHex) { mutableStateOf(settings.accentHex) }
    var importDraft by remember { mutableStateOf("") }
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(settings.accentHex) {
        val color = parseHexColor(settings.accentHex, MaterialTheme.colorScheme.primary)
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        accentHexDraft = settings.accentHex
    }

    val presets = listOf(
        "#0B6E5B",
        "#1363DF",
        "#C44536",
        "#F59E0B",
        "#7E22CE",
        "#EC4899",
    )

    fun updateGeneratedColor(newHue: Float = hue, newSaturation: Float = saturation, newValue: Float = value) {
        val generatedHex = hsvToColor(newHue, newSaturation, newValue).toHexColor()
        accentHexDraft = generatedHex
        onSetAccentHex(generatedHex)
    }

    LazyColumn(
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            Text(
                text = "Personalization",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            EnumOptions(
                selected = settings.personalizationLevel,
                values = PersonalizationLevel.entries,
                label = { it.name },
                onSelect = onSetPersonalization,
            )
        }
        item {
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

        item { HorizontalDivider() }

        item {
            Text(text = "Appearance", style = MaterialTheme.typography.titleMedium)
        }
        item {
            EnumOptions(
                selected = settings.themeMode,
                values = ThemeMode.entries,
                label = { it.name },
                onSelect = onSetThemeMode,
            )
        }
        item {
            Text(
                text = "Color presets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                color = parseHexColor(hex, MaterialTheme.colorScheme.primary),
                                shape = androidx.compose.foundation.shape.CircleShape,
                            )
                            .clickable {
                                accentHexDraft = hex
                                onSetAccentHex(hex)
                            },
                    )
                }
            }
        }

        item {
            Text(
                text = "Color generator",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            val generated = hsvToColor(hue, saturation, value)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = generated, shape = androidx.compose.foundation.shape.CircleShape),
            )
        }

        item {
            Text("Hue: ${hue.roundToInt()}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = hue,
                onValueChange = {
                    hue = it
                    updateGeneratedColor(newHue = it)
                },
                valueRange = 0f..360f,
            )
            Text("Saturation: ${(saturation * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = saturation,
                onValueChange = {
                    saturation = it
                    updateGeneratedColor(newSaturation = it)
                },
                valueRange = 0f..1f,
            )
            Text("Brightness: ${(value * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = value,
                onValueChange = {
                    value = it
                    updateGeneratedColor(newValue = it)
                },
                valueRange = 0f..1f,
            )
        }

        item {
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

        item { HorizontalDivider() }

        item {
            Text(text = "Accessibility", style = MaterialTheme.typography.titleMedium)
        }

        item {
            Text(
                text = "Font size (${(settings.fontScale * 100).roundToInt()}%)",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = settings.fontScale,
                onValueChange = onSetFontScale,
                valueRange = 0.85f..1.35f,
            )
        }

        item {
            SettingSwitchRow(
                label = "High contrast mode",
                checked = settings.highContrast,
                onCheckedChange = onSetHighContrast,
            )
        }

        item {
            SettingSwitchRow(
                label = "Reduce motion",
                checked = settings.reduceMotion,
                onCheckedChange = onSetReduceMotion,
            )
        }

        item {
            SettingSwitchRow(
                label = "Wi-Fi only downloads",
                checked = settings.wifiOnlyDownloads,
                onCheckedChange = onSetWifiOnly,
            )
        }

        item { HorizontalDivider() }

        item {
            Text(text = "Settings backup", style = MaterialTheme.typography.titleMedium)
        }
        item {
            TextButton(onClick = onExportSettings) {
                Text("Export settings (copy)")
            }
        }
        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = importDraft,
                onValueChange = { importDraft = it },
                label = { Text("Import settings JSON") },
                placeholder = { Text("{\"themeMode\":\"DARK\", ...}") },
            )
        }
        item {
            TextButton(
                onClick = { onImportSettings(importDraft) },
                enabled = importDraft.isNotBlank(),
            ) {
                Text("Import settings")
            }
        }

        item { HorizontalDivider() }

        item {
            Text(text = "Attribution", style = MaterialTheme.typography.titleMedium)
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
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
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

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val hsv = floatArrayOf(hue, saturation, value)
    return Color(AndroidColor.HSVToColor(hsv))
}

private fun Color.toHexColor(): String {
    val argb = toArgb()
    return String.format(
        "#%02X%02X%02X",
        AndroidColor.red(argb),
        AndroidColor.green(argb),
        AndroidColor.blue(argb),
    )
}
