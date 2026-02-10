package com.feelbachelor.doompedia.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

val BaseTypography = Typography()

fun scaledTypography(scale: Float): Typography {
    val factor = scale.coerceIn(0.85f, 1.35f)
    return BaseTypography.copy(
        displayLarge = BaseTypography.displayLarge.scaleFont(factor),
        displayMedium = BaseTypography.displayMedium.scaleFont(factor),
        displaySmall = BaseTypography.displaySmall.scaleFont(factor),
        headlineLarge = BaseTypography.headlineLarge.scaleFont(factor),
        headlineMedium = BaseTypography.headlineMedium.scaleFont(factor),
        headlineSmall = BaseTypography.headlineSmall.scaleFont(factor),
        titleLarge = BaseTypography.titleLarge.scaleFont(factor),
        titleMedium = BaseTypography.titleMedium.scaleFont(factor),
        titleSmall = BaseTypography.titleSmall.scaleFont(factor),
        bodyLarge = BaseTypography.bodyLarge.scaleFont(factor),
        bodyMedium = BaseTypography.bodyMedium.scaleFont(factor),
        bodySmall = BaseTypography.bodySmall.scaleFont(factor),
        labelLarge = BaseTypography.labelLarge.scaleFont(factor),
        labelMedium = BaseTypography.labelMedium.scaleFont(factor),
        labelSmall = BaseTypography.labelSmall.scaleFont(factor),
    )
}

private fun TextStyle.scaleFont(factor: Float): TextStyle {
    return copy(
        fontSize = fontSize * factor,
        lineHeight = lineHeight.scaleLineHeight(factor),
    )
}

private fun TextUnit.scaleLineHeight(factor: Float): TextUnit {
    if (!isSpecified) return this
    return this * factor
}
