package de.mm20.launcher2.themes

import de.mm20.launcher2.database.entities.ThemeEntity
import hct.Hct
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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

fun CorePaletteColor(color: String): CorePaletteColor? {
    return when (color) {
        "p" -> CorePaletteColor.Primary
        "s" -> CorePaletteColor.Secondary
        "t" -> CorePaletteColor.Tertiary
        "n" -> CorePaletteColor.Neutral
        "nv" -> CorePaletteColor.NeutralVariant
        "e" -> CorePaletteColor.Error
        else -> null
    }
}

sealed interface Color

internal fun Color(string: String?): Color? {
    if (string == null) return null
    if (string.startsWith("#")) {
        return StaticColor(string.substring(1).toLongOrNull(16)?.toInt() ?: return null)
    }
    if (string.startsWith("$")) {
        val parts = string.substring(1).split(".").takeIf { it.size == 2 } ?: return null
        val color = CorePaletteColor(parts[0]) ?: return null
        return ColorRef(
            color = color,
            tone = parts[1].toIntOrNull() ?: return null,
        )
    }
    return null
}

data class ColorRef(
    val color: CorePaletteColor,
    val tone: Int,
) : Color {
    override fun toString(): String {
        return "\$$color.$tone"
    }
}

@JvmInline
value class StaticColor(val color: Int) : Color {
    override fun toString(): String {
        return "#${color.toUInt().toString(16).padStart(8, '0')}"
    }
}

@Serializable
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

@Serializable
data class ColorScheme<out T : Color?>(
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

@Serializable
data class Theme(
    @Transient val id: UUID = UUID.randomUUID(),
    val builtIn: Boolean = false,
    val name: String,
    val corePalette: PartialCorePalette = EmptyCorePalette,
    val lightColorScheme: PartialColorScheme = DefaultLightColorScheme,
    val darkColorScheme: PartialColorScheme = DefaultDarkColorScheme,
) {

    constructor(entity: ThemeEntity) : this(
        id = entity.id,
        builtIn = false,
        name = entity.name,
        corePalette = CorePalette(
            primary = entity.corePaletteA1,
            secondary = entity.corePaletteA2,
            tertiary = entity.corePaletteA3,
            neutral = entity.corePaletteN1,
            neutralVariant = entity.corePaletteN2,
            error = entity.corePaletteE,
        ),
        lightColorScheme = ColorScheme(
            primary = Color(entity.lightPrimary),
            onPrimary = Color(entity.lightOnPrimary),
            primaryContainer = Color(entity.lightPrimaryContainer),
            onPrimaryContainer = Color(entity.lightOnPrimaryContainer),
            secondary = Color(entity.lightSecondary),
            onSecondary = Color(entity.lightOnSecondary),
            secondaryContainer = Color(entity.lightSecondaryContainer),
            onSecondaryContainer = Color(entity.lightOnSecondaryContainer),
            tertiary = Color(entity.lightTertiary),
            onTertiary = Color(entity.lightOnTertiary),
            tertiaryContainer = Color(entity.lightTertiaryContainer),
            onTertiaryContainer = Color(entity.lightOnTertiaryContainer),
            error = Color(entity.lightError),
            onError = Color(entity.lightOnError),
            errorContainer = Color(entity.lightErrorContainer),
            onErrorContainer = Color(entity.lightOnErrorContainer),
            surface = Color(entity.lightSurface),
            onSurface = Color(entity.lightOnSurface),
            onSurfaceVariant = Color(entity.lightOnSurfaceVariant),
            outline = Color(entity.lightOutline),
            outlineVariant = Color(entity.lightOutlineVariant),
            inverseSurface = Color(entity.lightInverseSurface),
            inverseOnSurface = Color(entity.lightInverseOnSurface),
            inversePrimary = Color(entity.lightInversePrimary),
            surfaceDim = Color(entity.lightSurfaceDim),
            surfaceBright = Color(entity.lightSurfaceBright),
            surfaceContainerLowest = Color(entity.lightSurfaceContainerLowest),
            surfaceContainerLow = Color(entity.lightSurfaceContainerLow),
            surfaceContainer = Color(entity.lightSurfaceContainer),
            surfaceContainerHigh = Color(entity.lightSurfaceContainerHigh),
            surfaceContainerHighest = Color(entity.lightSurfaceContainerHighest),
            background = Color(entity.lightBackground),
            onBackground = Color(entity.lightOnBackground),
            surfaceTint = Color(entity.lightSurfaceTint),
            scrim = Color(entity.lightScrim),
            surfaceVariant = Color(entity.lightSurfaceVariant),
        ),
        darkColorScheme = ColorScheme(
            primary = Color(entity.darkPrimary),
            onPrimary = Color(entity.darkOnPrimary),
            primaryContainer = Color(entity.darkPrimaryContainer),
            onPrimaryContainer = Color(entity.darkOnPrimaryContainer),
            secondary = Color(entity.darkSecondary),
            onSecondary = Color(entity.darkOnSecondary),
            secondaryContainer = Color(entity.darkSecondaryContainer),
            onSecondaryContainer = Color(entity.darkOnSecondaryContainer),
            tertiary = Color(entity.darkTertiary),
            onTertiary = Color(entity.darkOnTertiary),
            tertiaryContainer = Color(entity.darkTertiaryContainer),
            onTertiaryContainer = Color(entity.darkOnTertiaryContainer),
            error = Color(entity.darkError),
            onError = Color(entity.darkOnError),
            errorContainer = Color(entity.darkErrorContainer),
            onErrorContainer = Color(entity.darkOnErrorContainer),
            surface = Color(entity.darkSurface),
            onSurface = Color(entity.darkOnSurface),
            onSurfaceVariant = Color(entity.darkOnSurfaceVariant),
            outline = Color(entity.darkOutline),
            outlineVariant = Color(entity.darkOutlineVariant),
            inverseSurface = Color(entity.darkInverseSurface),
            inverseOnSurface = Color(entity.darkInverseOnSurface),
            inversePrimary = Color(entity.darkInversePrimary),
            surfaceDim = Color(entity.darkSurfaceDim),
            surfaceBright = Color(entity.darkSurfaceBright),
            surfaceContainerLowest = Color(entity.darkSurfaceContainerLowest),
            surfaceContainerLow = Color(entity.darkSurfaceContainerLow),
            surfaceContainer = Color(entity.darkSurfaceContainer),
            surfaceContainerHigh = Color(entity.darkSurfaceContainerHigh),
            surfaceContainerHighest = Color(entity.darkSurfaceContainerHighest),
            background = Color(entity.darkBackground),
            onBackground = Color(entity.darkOnBackground),
            surfaceTint = Color(entity.darkSurfaceTint),
            scrim = Color(entity.darkScrim),
            surfaceVariant = Color(entity.darkSurfaceVariant),
        ),
    )


    internal fun toEntity(): ThemeEntity {
        return ThemeEntity(
            id = id,
            name = name,
            corePaletteA1 = corePalette.primary,
            corePaletteA2 = corePalette.secondary,
            corePaletteA3 = corePalette.tertiary,
            corePaletteN1 = corePalette.neutral,
            corePaletteN2 = corePalette.neutralVariant,
            corePaletteE = corePalette.error,

            lightPrimary = lightColorScheme.primary?.toString(),
            lightOnPrimary = lightColorScheme.onPrimary?.toString(),
            lightPrimaryContainer = lightColorScheme.primaryContainer?.toString(),
            lightOnPrimaryContainer = lightColorScheme.onPrimaryContainer?.toString(),
            lightSecondary = lightColorScheme.secondary?.toString(),
            lightOnSecondary = lightColorScheme.onSecondary?.toString(),
            lightSecondaryContainer = lightColorScheme.secondaryContainer?.toString(),
            lightOnSecondaryContainer = lightColorScheme.onSecondaryContainer?.toString(),
            lightTertiary = lightColorScheme.tertiary?.toString(),
            lightOnTertiary = lightColorScheme.onTertiary?.toString(),
            lightTertiaryContainer = lightColorScheme.tertiaryContainer?.toString(),
            lightOnTertiaryContainer = lightColorScheme.onTertiaryContainer?.toString(),
            lightError = lightColorScheme.error?.toString(),
            lightOnError = lightColorScheme.onError?.toString(),
            lightErrorContainer = lightColorScheme.errorContainer?.toString(),
            lightOnErrorContainer = lightColorScheme.onErrorContainer?.toString(),
            lightSurface = lightColorScheme.surface?.toString(),
            lightOnSurface = lightColorScheme.onSurface?.toString(),
            lightOnSurfaceVariant = lightColorScheme.onSurfaceVariant?.toString(),
            lightOutline = lightColorScheme.outline?.toString(),
            lightOutlineVariant = lightColorScheme.outlineVariant?.toString(),
            lightInverseSurface = lightColorScheme.inverseSurface?.toString(),
            lightInverseOnSurface = lightColorScheme.inverseOnSurface?.toString(),
            lightInversePrimary = lightColorScheme.inversePrimary?.toString(),
            lightSurfaceDim = lightColorScheme.surfaceDim?.toString(),
            lightSurfaceBright = lightColorScheme.surfaceBright?.toString(),
            lightSurfaceContainerLowest = lightColorScheme.surfaceContainerLowest?.toString(),
            lightSurfaceContainerLow = lightColorScheme.surfaceContainerLow?.toString(),
            lightSurfaceContainer = lightColorScheme.surfaceContainer?.toString(),
            lightSurfaceContainerHigh = lightColorScheme.surfaceContainerHigh?.toString(),
            lightSurfaceContainerHighest = lightColorScheme.surfaceContainerHighest?.toString(),
            lightBackground = lightColorScheme.background?.toString(),
            lightOnBackground = lightColorScheme.onBackground?.toString(),
            lightSurfaceTint = lightColorScheme.surfaceTint?.toString(),
            lightScrim = lightColorScheme.scrim?.toString(),
            lightSurfaceVariant = lightColorScheme.surfaceVariant?.toString(),

            darkPrimary = darkColorScheme.primary?.toString(),
            darkOnPrimary = darkColorScheme.onPrimary?.toString(),
            darkPrimaryContainer = darkColorScheme.primaryContainer?.toString(),
            darkOnPrimaryContainer = darkColorScheme.onPrimaryContainer?.toString(),
            darkSecondary = darkColorScheme.secondary?.toString(),
            darkOnSecondary = darkColorScheme.onSecondary?.toString(),
            darkSecondaryContainer = darkColorScheme.secondaryContainer?.toString(),
            darkOnSecondaryContainer = darkColorScheme.onSecondaryContainer?.toString(),
            darkTertiary = darkColorScheme.tertiary?.toString(),
            darkOnTertiary = darkColorScheme.onTertiary?.toString(),
            darkTertiaryContainer = darkColorScheme.tertiaryContainer?.toString(),
            darkOnTertiaryContainer = darkColorScheme.onTertiaryContainer?.toString(),
            darkError = darkColorScheme.error?.toString(),
            darkOnError = darkColorScheme.onError?.toString(),
            darkErrorContainer = darkColorScheme.errorContainer?.toString(),
            darkOnErrorContainer = darkColorScheme.onErrorContainer?.toString(),
            darkSurface = darkColorScheme.surface?.toString(),
            darkOnSurface = darkColorScheme.onSurface?.toString(),
            darkOnSurfaceVariant = darkColorScheme.onSurfaceVariant?.toString(),
            darkOutline = darkColorScheme.outline?.toString(),
            darkOutlineVariant = darkColorScheme.outlineVariant?.toString(),
            darkInverseSurface = darkColorScheme.inverseSurface?.toString(),
            darkInverseOnSurface = darkColorScheme.inverseOnSurface?.toString(),
            darkInversePrimary = darkColorScheme.inversePrimary?.toString(),
            darkSurfaceDim = darkColorScheme.surfaceDim?.toString(),
            darkSurfaceBright = darkColorScheme.surfaceBright?.toString(),
            darkSurfaceContainerLowest = darkColorScheme.surfaceContainerLowest?.toString(),
            darkSurfaceContainerLow = darkColorScheme.surfaceContainerLow?.toString(),
            darkSurfaceContainer = darkColorScheme.surfaceContainer?.toString(),
            darkSurfaceContainerHigh = darkColorScheme.surfaceContainerHigh?.toString(),
            darkSurfaceContainerHighest = darkColorScheme.surfaceContainerHighest?.toString(),
            darkBackground = darkColorScheme.background?.toString(),
            darkOnBackground = darkColorScheme.onBackground?.toString(),
            darkSurfaceTint = darkColorScheme.surfaceTint?.toString(),
            darkScrim = darkColorScheme.scrim?.toString(),
            darkSurfaceVariant = darkColorScheme.surfaceVariant?.toString(),
        )
    }
}

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