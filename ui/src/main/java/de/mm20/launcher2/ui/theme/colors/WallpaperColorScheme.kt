package de.mm20.launcher2.ui.theme.colors

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import de.mm20.launcher2.ui.theme.WallpaperColors

class WallpaperColorScheme(
    wallpaperColors: WallpaperColors
) : ColorScheme() {
    override val neutral1: ColorSwatch
    override val neutral2: ColorSwatch
    override val accent1: ColorSwatch
    override val accent2: ColorSwatch
    override val accent3: ColorSwatch

    init {
        val primary = wallpaperColors.primary
        val secondary = wallpaperColors.secondary
        val tertiary = wallpaperColors.tertiary

        val neutral = primary.takeIf { !isBrown(it) }
            ?: secondary?.takeIf { !isBrown(it) }
            ?: tertiary?.takeIf { isBrown(it) }
            ?: primary

        val acc1: Color = tertiary?.takeIf { it != neutral }
            ?: primary.takeIf { it != neutral }
            ?: secondary?.takeIf { it != neutral }
            ?: primary

        val acc2: Color = secondary?.takeIf { it != neutral }
            ?: primary.takeIf { it != neutral }
            ?: tertiary?.takeIf { it != neutral }
            ?: primary


        neutral1 = colorSwatch(neutral)
        neutral2 = neutral1
        accent1 = colorSwatch(acc1)
        accent2 = colorSwatch(acc2)
        accent3 = neutral1
    }

    private fun isBrown(color: Color): Boolean {
        val hsl = floatArrayOf(0f, 0f, 0f)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        return hsl[0] in 0.0..50.0
    }
}