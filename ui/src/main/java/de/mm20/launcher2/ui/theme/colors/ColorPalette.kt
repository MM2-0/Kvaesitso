package de.mm20.launcher2.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

abstract class ColorPalette {
    abstract val neutral: ColorSwatch
    abstract val neutralVariant: ColorSwatch

    abstract val primary: ColorSwatch
    abstract val secondary: ColorSwatch
    abstract val tertiary: ColorSwatch
}

fun ColorPalette.toDarkColorScheme() : ColorScheme {
    return darkColorScheme(
        primary = primary.shade80,
        onPrimary = primary.shade20,
        primaryContainer = primary.shade30,
        onPrimaryContainer = primary.shade90,
        secondary = secondary.shade80,
        onSecondary = secondary.shade20,
        secondaryContainer = secondary.shade30,
        onSecondaryContainer = secondary.shade90,
        tertiary = tertiary.shade80,
        onTertiary = tertiary.shade20,
        tertiaryContainer = tertiary.shade30,
        onTertiaryContainer = tertiary.shade90,
        background = neutral.shade10,
        onBackground = neutral.shade90,
        surface = neutral.shade10,
        onSurface = neutral.shade80,
        surfaceVariant = neutralVariant.shade30,
        onSurfaceVariant = neutralVariant.shade80,
        outline = neutralVariant.shade60,
        inverseOnSurface = neutralVariant.shade20,
        inverseSurface = neutralVariant.shade90,
    )
}

fun ColorPalette.toLightColorScheme() : ColorScheme {
    return lightColorScheme(
        primary = primary.shade40,
        onPrimary = primary.shade100,
        primaryContainer = primary.shade90,
        onPrimaryContainer = primary.shade10,
        secondary = secondary.shade40,
        onSecondary = secondary.shade100,
        secondaryContainer = secondary.shade90,
        onSecondaryContainer = secondary.shade10,
        tertiary = tertiary.shade40,
        onTertiary = tertiary.shade100,
        tertiaryContainer = tertiary.shade90,
        onTertiaryContainer = tertiary.shade10,
        background = neutral.shade99,
        onBackground = neutral.shade10,
        surface = neutral.shade99,
        onSurface = neutral.shade10,
        surfaceVariant = neutralVariant.shade90,
        onSurfaceVariant = neutralVariant.shade30,
        outline = neutralVariant.shade50,
        inverseOnSurface = neutralVariant.shade95,
        inverseSurface = neutralVariant.shade20,
    )
}