package de.mm20.launcher2.ui.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import de.mm20.launcher2.ui.theme.WallpaperColors
import palettes.TonalPalette

fun WallpaperColorScheme(wallpaperColors: WallpaperColors, darkTheme: Boolean): ColorScheme {
    val primary = TonalPalette.fromInt(wallpaperColors.primary.toArgb())
    val secondary = TonalPalette.fromInt((wallpaperColors.secondary ?: wallpaperColors.primary).toArgb())
    val tertiary = TonalPalette.fromInt((wallpaperColors.tertiary ?: wallpaperColors.primary).toArgb())

    val neutral1 = TonalPalette.fromInt(Color.Black.toArgb())
    val neutral2 = TonalPalette.fromInt(Color.Black.toArgb())

    return if(darkTheme) {
         darkColorScheme(
             primary = Color(primary.tone(80)),
             onPrimary = Color(primary.tone(20)),
             primaryContainer = Color(primary.tone(30)),
             onPrimaryContainer = Color(primary.tone(90)),
             secondary = Color(secondary.tone(80)),
             onSecondary = Color(secondary.tone(20)),
             secondaryContainer = Color(secondary.tone(30)),
             onSecondaryContainer = Color(secondary.tone(90)),
             tertiary = Color(tertiary.tone(80)),
             onTertiary = Color(tertiary.tone(20)),
             tertiaryContainer = Color(tertiary.tone(30)),
             onTertiaryContainer = Color(tertiary.tone(90)),
             background = Color(neutral1.tone(10)),
             onBackground = Color(neutral1.tone(90)),
             surface = Color(neutral1.tone(10)),
             onSurface = Color(neutral1.tone(90)),
             surfaceVariant = Color(neutral2.tone(30)),
             onSurfaceVariant = Color(neutral2.tone(80)),
             outline = Color(neutral2.tone(60)),
             inverseSurface = Color(neutral1.tone(90)),
             inverseOnSurface = Color(neutral1.tone(20)),
             inversePrimary = Color(primary.tone(40)),
         )
    } else {
        lightColorScheme(
            primary = Color(primary.tone(40)),
            onPrimary = Color(primary.tone(100)),
            primaryContainer = Color(primary.tone(90)),
            onPrimaryContainer = Color(primary.tone(10)),
            secondary = Color(secondary.tone(40)),
            onSecondary = Color(secondary.tone(100)),
            secondaryContainer = Color(secondary.tone(90)),
            onSecondaryContainer = Color(secondary.tone(10)),
            tertiary = Color(tertiary.tone(40)),
            onTertiary = Color(tertiary.tone(100)),
            tertiaryContainer = Color(tertiary.tone(90)),
            onTertiaryContainer = Color(tertiary.tone(10)),
            background = Color(neutral1.tone(99)),
            onBackground = Color(neutral1.tone(10)),
            surface = Color(neutral1.tone(99)),
            onSurface = Color(neutral1.tone(10)),
            surfaceVariant = Color(neutral2.tone(90)),
            onSurfaceVariant = Color(neutral2.tone(30)),
            outline = Color(neutral2.tone(50)),
            inverseSurface = Color(neutral1.tone(20)),
            inverseOnSurface = Color(neutral1.tone(95)),
            inversePrimary = Color(primary.tone(80)),)
    }
}