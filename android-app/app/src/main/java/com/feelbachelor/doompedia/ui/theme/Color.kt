package com.feelbachelor.doompedia.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import android.graphics.Color as AndroidColor

val LightPrimary = Color(0xFF0B5C4A)
val LightSecondary = Color(0xFF5B665F)
val LightBackground = Color(0xFFF5F7F3)
val LightSurface = Color(0xFFFFFFFF)

val DarkPrimary = Color(0xFF8CDCC2)
val DarkSecondary = Color(0xFFAAB8B0)
val DarkBackground = Color(0xFF111511)
val DarkSurface = Color(0xFF191F1A)

fun parseHexColor(hex: String, fallback: Color): Color {
    val value = hex.trim()
    if (value.isBlank()) return fallback
    val normalized = when {
        value.startsWith("#") -> value
        value.length == 6 || value.length == 8 -> "#$value"
        else -> return fallback
    }
    return runCatching {
        Color(AndroidColor.parseColor(normalized))
    }.getOrElse { fallback }
}

fun softenedColor(base: Color, targetBackground: Color): Color {
    return lerp(base, targetBackground, 0.25f)
}
