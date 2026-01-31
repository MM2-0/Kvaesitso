package de.mm20.launcher2.ui.theme.typography.fontfamily

import androidx.annotation.FontRes
import androidx.collection.LruCache
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File


private val fontCache = LruCache<String, FontFamily>(20)

internal fun createVariableFontFamily(@FontRes resId: Int, settings: Map<String, Float>): FontFamily {
    val cacheKey = "0.$resId/${settings.entries.joinToString(separator = ",") { "${it.key}=${it.value}" }}"

    val cacheHit = fontCache.get(cacheKey)
    if (cacheHit != null) {
        return cacheHit
    }

    val family = createVariableFontFamily(
        resId, *settings.map { FontVariation.Setting(it.key, it.value) }.toTypedArray()
    )

    fontCache.put(cacheKey, family)

    return family
}

private fun createVariableFontFamily(@FontRes resId: Int, vararg settings: FontVariation.Setting): FontFamily {
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
