package de.mm20.launcher2.ui.theme.colorscheme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.themes.CorePalette
import de.mm20.launcher2.themes.DefaultDarkColorScheme
import de.mm20.launcher2.themes.DefaultLightColorScheme
import de.mm20.launcher2.themes.FullColorScheme
import de.mm20.launcher2.themes.PartialCorePalette
import de.mm20.launcher2.themes.Theme
import de.mm20.launcher2.themes.get
import de.mm20.launcher2.themes.merge
import de.mm20.launcher2.ui.locals.LocalWallpaperColors

@Composable
fun lightColorSchemeOf(theme: Theme): ColorScheme {
    return colorSchemeOf(theme.lightColorScheme.merge(DefaultLightColorScheme), theme.corePalette)
}

@Composable
fun darkColorSchemeOf(theme: Theme): ColorScheme {
    return colorSchemeOf(theme.darkColorScheme.merge(DefaultDarkColorScheme), theme.corePalette)
}

@Composable
fun colorSchemeOf(colorScheme: FullColorScheme, corePalette: PartialCorePalette): ColorScheme {
    val defaultPalette = systemCorePalette()
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
fun systemCorePalette(): CorePalette<Int> {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= 31) {
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
        val corePalette = palettes.CorePalette.of(wallpaperColors.primary.toArgb())
        CorePalette(
            primary = corePalette.a1.tone(40),
            secondary = corePalette.a2.tone(40),
            tertiary = corePalette.a3.tone(40),
            neutral = corePalette.n1.tone(40),
            neutralVariant = corePalette.n2.tone(40),
            error = corePalette.error.tone(40),
        )
    }
}

fun CustomColorScheme(colors: Settings.AppearanceSettings.CustomColors.Scheme): ColorScheme {
    return ColorScheme(
        primary = Color(colors.primary),
        onPrimary = Color(colors.onPrimary),
        primaryContainer = Color(colors.primaryContainer),
        onPrimaryContainer = Color(colors.onPrimaryContainer),
        secondary = Color(colors.secondary),
        onSecondary = Color(colors.onSecondary),
        secondaryContainer = Color(colors.secondaryContainer),
        onSecondaryContainer = Color(colors.onSecondaryContainer),
        tertiary = Color(colors.tertiary),
        onTertiary = Color(colors.onTertiary),
        tertiaryContainer = Color(colors.tertiaryContainer),
        onTertiaryContainer = Color(colors.onTertiaryContainer),
        background = Color(colors.background),
        onBackground = Color(colors.onBackground),
        surface = Color(colors.surface),
        onSurface = Color(colors.onSurface),
        surfaceVariant = Color(colors.surfaceVariant),
        onSurfaceVariant = Color(colors.onSurfaceVariant),
        outline = Color(colors.outline),
        inverseSurface = Color(colors.inverseSurface),
        inverseOnSurface = Color(colors.inverseOnSurface),
        inversePrimary = Color(colors.inversePrimary),
        surfaceTint = Color(colors.surfaceTint),
        error = Color(colors.error),
        onError = Color(colors.onError),
        errorContainer = Color(colors.errorContainer),
        onErrorContainer = Color(colors.onErrorContainer),
        outlineVariant = Color(colors.outlineVariant),
        scrim = Color(colors.scrim),
        surfaceBright = Color(colors.surfaceBright),
        surfaceDim = Color(colors.surfaceDim),
        surfaceContainer = Color(colors.surfaceContainer),
        surfaceContainerHigh = Color(colors.surfaceContainerHigh),
        surfaceContainerHighest = Color(colors.surfaceContainerHighest),
        surfaceContainerLow = Color(colors.surfaceContainerLow),
        surfaceContainerLowest = Color(colors.surfaceContainerLowest),
    )
}