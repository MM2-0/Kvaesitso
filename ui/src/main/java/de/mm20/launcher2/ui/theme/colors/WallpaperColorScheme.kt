package de.mm20.launcher2.ui.theme.colors

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import de.mm20.launcher2.ui.theme.WallpaperColors

class WallpaperColorScheme(
    val wallpaperColors: WallpaperColors
): ColorScheme() {
    override val neutral1 = colorSwatch(wallpaperColors.primary)
    override val neutral2 = colorSwatch(wallpaperColors.primary)
    override val accent1 = colorSwatch(wallpaperColors.primary)
    override val accent2 = colorSwatch(wallpaperColors.secondary ?: wallpaperColors.primary)
    override val accent3 = colorSwatch(wallpaperColors.tertiary ?: wallpaperColors.primary)
}