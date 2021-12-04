package de.mm20.launcher2.ui.theme.colors

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import de.mm20.launcher2.ui.theme.WallpaperColors

class WallpaperColorPalette(
    wallpaperColors: WallpaperColors
) : ColorPalette() {
    override val neutral: ColorSwatch
    override val neutralVariant: ColorSwatch
    override val primary: ColorSwatch
    override val secondary: ColorSwatch
    override val tertiary: ColorSwatch

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


        this.neutral = colorSwatch(neutral)
        neutralVariant = this.neutral
        this.primary = colorSwatch(acc1)
        this.secondary = colorSwatch(acc2)
        this.tertiary = this.neutral
    }

    private fun isBrown(color: Color): Boolean {
        val hsl = floatArrayOf(0f, 0f, 0f)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        return hsl[0] in 0.0..50.0
    }
}