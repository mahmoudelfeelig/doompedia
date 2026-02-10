package com.feelbachelor.doompedia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    background = LightBackground,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
)

@Composable
fun DoompediaTheme(
    forceDark: Boolean? = null,
    accentHex: String = "",
    content: @Composable () -> Unit,
) {
    val darkTheme = forceDark ?: isSystemInDarkTheme()
    val baseScheme = if (darkTheme) DarkColors else LightColors
    val fallbackAccent = if (darkTheme) DarkPrimary else LightPrimary
    val accent = parseHexColor(accentHex, fallbackAccent)
    val scheme = baseScheme.copy(
        primary = accent,
        secondary = softenedColor(accent, baseScheme.background),
        tertiary = softenedColor(accent, baseScheme.surface),
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}
