package de.mm20.launcher2.ui.theme.typography.fontfamily

import androidx.annotation.FontRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

internal fun FontVariation.roundness(roundness: Float): FontVariation.Setting {
    return Setting("ROND", roundness)
}

internal fun createVariableFontFamily(@FontRes resId: Int, vararg settings: FontVariation.Setting): FontFamily {
    val fonts = mutableListOf<Font>()
    for (weight in 100..900 step 100) {
        fonts += Font(
            resId = resId,
            weight = FontWeight(weight),
            style = FontStyle.Normal,
            variationSettings =
                FontVariation.Settings(
                    weight = FontWeight(weight),
                    style = FontStyle.Normal,
                    *settings
                )
        )
        fonts += Font(
            resId = resId,
            weight = FontWeight(weight),
            style = FontStyle.Italic,
            variationSettings =
                FontVariation.Settings(
                    weight = FontWeight(weight),
                    style = FontStyle.Italic,
                    *settings
                )
        )
    }
    return FontFamily(fonts)
}