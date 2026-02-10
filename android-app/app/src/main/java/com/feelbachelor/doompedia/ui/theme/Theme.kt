package com.feelbachelor.doompedia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    fontScale: Float = 1.0f,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = forceDark ?: isSystemInDarkTheme()
    val baseScheme = if (darkTheme) DarkColors else LightColors
    val fallbackAccent = if (darkTheme) DarkPrimary else LightPrimary
    val accent = parseHexColor(accentHex, fallbackAccent)
    val accentedScheme = baseScheme.copy(
        primary = accent,
        secondary = softenedColor(accent, baseScheme.background),
        tertiary = softenedColor(accent, baseScheme.surface),
    )
    val scheme = if (highContrast) {
        if (darkTheme) {
            accentedScheme.copy(
                background = Color(0xFF000000),
                surface = Color(0xFF000000),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF),
            )
        } else {
            accentedScheme.copy(
                background = Color(0xFFFFFFFF),
                surface = Color(0xFFFFFFFF),
                onBackground = Color(0xFF000000),
                onSurface = Color(0xFF000000),
            )
        }
    } else {
        accentedScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = scaledTypography(fontScale),
        content = content,
    )
}
