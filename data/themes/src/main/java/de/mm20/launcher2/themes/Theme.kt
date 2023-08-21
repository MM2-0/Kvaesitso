package de.mm20.launcher2.themes

import hct.Hct
import java.util.UUID

enum class CorePaletteColor {
    Primary,
    Secondary,
    Tertiary,
    Neutral,
    NeutralVariant,
    Error;

    override fun toString(): String {
        return when (this) {
            Primary -> "p"
            Secondary -> "s"
            Tertiary -> "t"
            Neutral -> "n"
            NeutralVariant -> "nv"
            Error -> "e"
        }
    }
}

sealed interface Color

data class ColorRef(
    val color: CorePaletteColor,
    val tone: Int,
) : Color {
    override fun toString(): String {
        return "\$${color.name}.$tone"
    }
}

@JvmInline
value class StaticColor(val color: Int) : Color {
    override fun toString(): String {
        return "#${color.toString(16).padStart(6, '0')}"
    }
}

data class CorePalette<out T : Int?>(
    val primary: T,
    val secondary: T,
    val tertiary: T,
    val neutral: T,
    val neutralVariant: T,
    val error: T,
)

val EmptyCorePalette = CorePalette<Int?>(null, null, null, null, null, null)

typealias FullCorePalette = CorePalette<Int>
typealias PartialCorePalette = CorePalette<Int?>

data class ColorScheme<out T: Color?>(
    val primary: T,
    val onPrimary: T,
    val primaryContainer: T,
    val onPrimaryContainer: T,
    val secondary: T,
    val onSecondary: T,
    val secondaryContainer: T,
    val onSecondaryContainer: T,
    val tertiary: T,
    val onTertiary: T,
    val tertiaryContainer: T,
    val onTertiaryContainer: T,
    val error: T,
    val onError: T,
    val errorContainer: T,
    val onErrorContainer: T,
    val surface: T,
    val onSurface: T,
    val onSurfaceVariant: T,
    val outline: T,
    val outlineVariant: T,
    val inverseSurface: T,
    val inverseOnSurface: T,
    val inversePrimary: T,
    val surfaceDim: T,
    val surfaceBright: T,
    val surfaceContainerLowest: T,
    val surfaceContainerLow: T,
    val surfaceContainer: T,
    val surfaceContainerHigh: T,
    val surfaceContainerHighest: T,

    val background: T,
    val onBackground: T,
    val surfaceTint: T,
    val scrim: T,
    val surfaceVariant: T,
)

typealias FullColorScheme = ColorScheme<Color>
typealias PartialColorScheme = ColorScheme<Color?>

data class Theme(
    val id: UUID,
    val builtIn: Boolean = false,
    val name: String,
    val corePalette: PartialCorePalette = EmptyCorePalette,
    val lightColorScheme: PartialColorScheme = DefaultLightColorScheme,
    val darkColorScheme: PartialColorScheme = DefaultDarkColorScheme,
)

fun <T : Int?> CorePalette<T>.get(color: CorePaletteColor): T {
    return when (color) {
        CorePaletteColor.Primary -> primary
        CorePaletteColor.Secondary -> secondary
        CorePaletteColor.Tertiary -> tertiary
        CorePaletteColor.Neutral -> neutral
        CorePaletteColor.NeutralVariant -> neutralVariant
        CorePaletteColor.Error -> error
    }
}

fun Color.get(corePalette: FullCorePalette): Int {
    return when (this) {
        is StaticColor -> color
        is ColorRef -> {
            corePalette.get(this.color).atTone(this.tone)
        }
    }
}

fun Int.atTone(tone: Int): Int {
    return Hct.fromInt(this).apply {
        setTone(tone.toDouble())
    }.toInt()
}

fun PartialCorePalette.merge(other: FullCorePalette): FullCorePalette {
    return CorePalette(
        primary = this.primary ?: other.primary,
        secondary = this.secondary ?: other.secondary,
        tertiary = this.tertiary ?: other.tertiary,
        neutral = this.neutral ?: other.neutral,
        neutralVariant = this.neutralVariant ?: other.neutralVariant,
        error = this.error ?: other.error,
    )
}

fun PartialColorScheme.merge(other: FullColorScheme): FullColorScheme {
    return ColorScheme(
        primary = this.primary ?: other.primary,
        onPrimary = this.onPrimary ?: other.onPrimary,
        primaryContainer = this.primaryContainer ?: other.primaryContainer,
        onPrimaryContainer = this.onPrimaryContainer ?: other.onPrimaryContainer,
        secondary = this.secondary ?: other.secondary,
        onSecondary = this.onSecondary ?: other.onSecondary,
        secondaryContainer = this.secondaryContainer ?: other.secondaryContainer,
        onSecondaryContainer = this.onSecondaryContainer ?: other.onSecondaryContainer,
        tertiary = this.tertiary ?: other.tertiary,
        onTertiary = this.onTertiary ?: other.onTertiary,
        tertiaryContainer = this.tertiaryContainer ?: other.tertiaryContainer,
        onTertiaryContainer = this.onTertiaryContainer ?: other.onTertiaryContainer,
        error = this.error ?: other.error,
        onError = this.onError ?: other.onError,
        errorContainer = this.errorContainer ?: other.errorContainer,
        onErrorContainer = this.onErrorContainer ?: other.onErrorContainer,
        surfaceDim = this.surfaceDim ?: other.surfaceDim,
        surface = this.surface ?: other.surface,
        surfaceBright = this.surfaceBright ?: other.surfaceBright,
        surfaceContainerLowest = this.surfaceContainerLowest ?: other.surfaceContainerLowest,
        surfaceContainerLow = this.surfaceContainerLow ?: other.surfaceContainerLow,
        surfaceContainer = this.surfaceContainer ?: other.surfaceContainer,
        surfaceContainerHigh = this.surfaceContainerHigh ?: other.surfaceContainerHigh,
        surfaceContainerHighest = this.surfaceContainerHighest ?: other.surfaceContainerHighest,
        onSurface = this.onSurface ?: other.onSurface,
        onSurfaceVariant = this.onSurfaceVariant ?: other.onSurfaceVariant,
        outline = this.outline ?: other.outline,
        outlineVariant = this.outlineVariant ?: other.outlineVariant,
        inverseSurface = this.inverseSurface ?: other.inverseSurface,
        inverseOnSurface = this.inverseOnSurface ?: other.inverseOnSurface,
        inversePrimary = this.inversePrimary ?: other.inversePrimary,
        surfaceVariant = this.surfaceVariant ?: other.surfaceVariant,
        scrim = this.scrim ?: other.scrim,
        onBackground = this.onBackground ?: other.onBackground,
        background = this.background ?: other.background,
        surfaceTint = this.surfaceTint ?: other.surfaceTint,
    )
}