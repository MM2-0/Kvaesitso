package de.mm20.launcher2.ui.theme.colorscheme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.colors.CorePalette
import de.mm20.launcher2.themes.colors.DefaultDarkColorScheme
import de.mm20.launcher2.themes.colors.DefaultLightColorScheme
import de.mm20.launcher2.themes.colors.FullColorScheme
import de.mm20.launcher2.themes.colors.PartialCorePalette
import de.mm20.launcher2.themes.colors.Colors as ThemeColors
import de.mm20.launcher2.themes.colors.get
import de.mm20.launcher2.themes.colors.merge
import de.mm20.launcher2.ui.locals.LocalWallpaperColors
import dynamiccolor.ColorSpec
import hct.Hct
import org.koin.compose.koinInject
import scheme.SchemeContent
import scheme.SchemeExpressive
import scheme.SchemeNeutral
import scheme.SchemeTonalSpot

@Composable
fun lightColorSchemeOf(colors: ThemeColors): ColorScheme {
    return colorSchemeOf(false, colors.lightColorScheme.merge(DefaultLightColorScheme), colors.corePalette)
}

@Composable
fun darkColorSchemeOf(colors: ThemeColors): ColorScheme {
    return colorSchemeOf(true, colors.darkColorScheme.merge(DefaultDarkColorScheme), colors.corePalette)
}

@Composable
fun colorSchemeOf(dark: Boolean, colorScheme: FullColorScheme, corePalette: PartialCorePalette): ColorScheme {
    val defaultPalette = systemCorePalette(dark)
    return remember(colorScheme, corePalette, defaultPalette) {
        val mergedCorePalette = corePalette.merge(defaultPalette)
        ColorScheme(
            primary = Color(colorScheme.primary.get(mergedCorePalette)),
            onPrimary = Color(colorScheme.onPrimary.get(mergedCorePalette)),
            primaryContainer = Color(colorScheme.primaryContainer.get(mergedCorePalette)),
            onPrimaryContainer = Color(colorScheme.onPrimaryContainer.get(mergedCorePalette)),
            secondary = Color(colorScheme.secondary.get(mergedCorePalette)),
            onSecondary = Color(colorScheme.onSecondary.get(mergedCorePalette)),
            secondaryContainer = Color(colorScheme.secondaryContainer.get(mergedCorePalette)),
            onSecondaryContainer = Color(colorScheme.onSecondaryContainer.get(mergedCorePalette)),
            tertiary = Color(colorScheme.tertiary.get(mergedCorePalette)),
            onTertiary = Color(colorScheme.onTertiary.get(mergedCorePalette)),
            tertiaryContainer = Color(colorScheme.tertiaryContainer.get(mergedCorePalette)),
            onTertiaryContainer = Color(colorScheme.onTertiaryContainer.get(mergedCorePalette)),
            error = Color(colorScheme.error.get(mergedCorePalette)),
            onError = Color(colorScheme.onError.get(mergedCorePalette)),
            errorContainer = Color(colorScheme.errorContainer.get(mergedCorePalette)),
            onErrorContainer = Color(colorScheme.onErrorContainer.get(mergedCorePalette)),
            surface = Color(colorScheme.surface.get(mergedCorePalette)),
            onSurface = Color(colorScheme.onSurface.get(mergedCorePalette)),
            onSurfaceVariant = Color(colorScheme.onSurfaceVariant.get(mergedCorePalette)),
            outline = Color(colorScheme.outline.get(mergedCorePalette)),
            outlineVariant = Color(colorScheme.outlineVariant.get(mergedCorePalette)),
            surfaceContainerLowest = Color(colorScheme.surfaceContainerLowest.get(mergedCorePalette)),
            surfaceContainerLow = Color(colorScheme.surfaceContainerLow.get(mergedCorePalette)),
            surfaceContainer = Color(colorScheme.surfaceContainer.get(mergedCorePalette)),
            surfaceContainerHigh = Color(colorScheme.surfaceContainerHigh.get(mergedCorePalette)),
            surfaceContainerHighest = Color(colorScheme.surfaceContainerHighest.get(mergedCorePalette)),
            surfaceDim = Color(colorScheme.surfaceDim.get(mergedCorePalette)),
            surfaceBright = Color(colorScheme.surfaceBright.get(mergedCorePalette)),
            inverseOnSurface = Color(colorScheme.inverseOnSurface.get(mergedCorePalette)),
            inverseSurface = Color(colorScheme.inverseSurface.get(mergedCorePalette)),
            inversePrimary = Color(colorScheme.inversePrimary.get(mergedCorePalette)),
            surfaceTint = Color(colorScheme.surfaceTint.get(mergedCorePalette)),
            background = Color(colorScheme.background.get(mergedCorePalette)),
            onBackground = Color(colorScheme.onBackground.get(mergedCorePalette)),
            scrim = Color(colorScheme.scrim.get(mergedCorePalette)),
            surfaceVariant = Color(colorScheme.surfaceVariant.get(mergedCorePalette)),
        )
    }
}

@Composable
fun systemCorePalette(dark: Boolean): CorePalette<Int> {
    val uiSettings: UiSettings = koinInject()
    val compatModeColors by remember {
        uiSettings.compatModeColors
    }.collectAsState(false)

    if (Build.VERSION.SDK_INT >= 31 && !compatModeColors) {
        val context = LocalContext.current
        return CorePalette(
            primary = ContextCompat.getColor(context, android.R.color.system_accent1_500),
            secondary = ContextCompat.getColor(context, android.R.color.system_accent2_500),
            tertiary = ContextCompat.getColor(context, android.R.color.system_accent3_500),
            neutral = ContextCompat.getColor(context, android.R.color.system_neutral1_500),
            neutralVariant = ContextCompat.getColor(context, android.R.color.system_neutral2_500),
            error = 0xFFB3261E.toInt(),
        )
    }
    val wallpaperColors = LocalWallpaperColors.current
    return remember(wallpaperColors) {
        val corePalette = SchemeTonalSpot(
            Hct.fromInt(
                wallpaperColors.primary.toArgb()),
            dark,
            0.0,
            ColorSpec.SpecVersion.SPEC_2025,
        )
        CorePalette(
            primary = corePalette.primaryPaletteKeyColor,
            secondary = corePalette.secondaryPaletteKeyColor,
            tertiary = corePalette.tertiaryPaletteKeyColor,
            neutral = corePalette.neutralPaletteKeyColor,
            neutralVariant = corePalette.neutralVariantPaletteKeyColor,
            error = corePalette.errorPalette.keyColor.toInt(),
        )
    }
}
