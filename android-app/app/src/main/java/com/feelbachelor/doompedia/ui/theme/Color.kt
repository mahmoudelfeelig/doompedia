package com.feelbachelor.doompedia.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import android.graphics.Color as AndroidColor

val LightPrimary = Color(0xFF004D3D)
val LightSecondary = Color(0xFF44665D)
val LightBackground = Color(0xFFF7FAF8)

val DarkPrimary = Color(0xFF7ED8BE)
val DarkSecondary = Color(0xFFA5CCC2)
val DarkBackground = Color(0xFF0D1714)

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
