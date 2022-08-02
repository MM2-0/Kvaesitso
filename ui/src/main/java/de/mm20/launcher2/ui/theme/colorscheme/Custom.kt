package de.mm20.launcher2.ui.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import de.mm20.launcher2.preferences.Settings

fun CustomColorScheme(colors: Settings.AppearanceSettings.CustomColors.Scheme) : ColorScheme {
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
        surfaceTint = Color(colors.primary),
        error = Color(colors.error),
        onError = Color(colors.onError),
        errorContainer = Color(colors.errorContainer),
        onErrorContainer = Color(colors.onErrorContainer),
        outlineVariant = Color(colors.outlineVariant),
        scrim = Color(colors.scrim),
    )
}