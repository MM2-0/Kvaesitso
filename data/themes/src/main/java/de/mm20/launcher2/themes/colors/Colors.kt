package de.mm20.launcher2.themes.colors

import de.mm20.launcher2.database.entities.ColorsEntity
import de.mm20.launcher2.serialization.ColorIntAsHexSerializer
import de.mm20.launcher2.serialization.UUIDSerializer
import hct.Hct
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Colors(
    @Serializable(with = UUIDSerializer::class) val id: UUID = UUID.randomUUID(),
    val builtIn: Boolean = false,
    val name: String,
    val corePalette: PartialCorePalette = EmptyCorePalette,
    val lightColorScheme: PartialColorScheme = DefaultLightColorScheme,
    val darkColorScheme: PartialColorScheme = DefaultDarkColorScheme,
) {

    constructor(entity: ColorsEntity) : this(
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
            primary = Color.fromString(entity.lightPrimary),
            onPrimary = Color.fromString(entity.lightOnPrimary),
            primaryContainer = Color.fromString(entity.lightPrimaryContainer),
            onPrimaryContainer = Color.fromString(entity.lightOnPrimaryContainer),
            secondary = Color.fromString(entity.lightSecondary),
            onSecondary = Color.fromString(entity.lightOnSecondary),
            secondaryContainer = Color.fromString(entity.lightSecondaryContainer),
            onSecondaryContainer = Color.fromString(entity.lightOnSecondaryContainer),
            tertiary = Color.fromString(entity.lightTertiary),
            onTertiary = Color.fromString(entity.lightOnTertiary),
            tertiaryContainer = Color.fromString(entity.lightTertiaryContainer),
            onTertiaryContainer = Color.fromString(entity.lightOnTertiaryContainer),
            error = Color.fromString(entity.lightError),
            onError = Color.fromString(entity.lightOnError),
            errorContainer = Color.fromString(entity.lightErrorContainer),
            onErrorContainer = Color.fromString(entity.lightOnErrorContainer),
            surface = Color.fromString(entity.lightSurface),
            onSurface = Color.fromString(entity.lightOnSurface),
            onSurfaceVariant = Color.fromString(entity.lightOnSurfaceVariant),
            outline = Color.fromString(entity.lightOutline),
            outlineVariant = Color.fromString(entity.lightOutlineVariant),
            inverseSurface = Color.fromString(entity.lightInverseSurface),
            inverseOnSurface = Color.fromString(entity.lightInverseOnSurface),
            inversePrimary = Color.fromString(entity.lightInversePrimary),
            surfaceDim = Color.fromString(entity.lightSurfaceDim),
            surfaceBright = Color.fromString(entity.lightSurfaceBright),
            surfaceContainerLowest = Color.fromString(entity.lightSurfaceContainerLowest),
            surfaceContainerLow = Color.fromString(entity.lightSurfaceContainerLow),
            surfaceContainer = Color.fromString(entity.lightSurfaceContainer),
            surfaceContainerHigh = Color.fromString(entity.lightSurfaceContainerHigh),
            surfaceContainerHighest = Color.fromString(entity.lightSurfaceContainerHighest),
            background = Color.fromString(entity.lightBackground),
            onBackground = Color.fromString(entity.lightOnBackground),
            surfaceTint = Color.fromString(entity.lightSurfaceTint),
            scrim = Color.fromString(entity.lightScrim),
            surfaceVariant = Color.fromString(entity.lightSurfaceVariant),
        ),
        darkColorScheme = ColorScheme(
            primary = Color.fromString(entity.darkPrimary),
            onPrimary = Color.fromString(entity.darkOnPrimary),
            primaryContainer = Color.fromString(entity.darkPrimaryContainer),
            onPrimaryContainer = Color.fromString(entity.darkOnPrimaryContainer),
            secondary = Color.fromString(entity.darkSecondary),
            onSecondary = Color.fromString(entity.darkOnSecondary),
            secondaryContainer = Color.fromString(entity.darkSecondaryContainer),
            onSecondaryContainer = Color.fromString(entity.darkOnSecondaryContainer),
            tertiary = Color.fromString(entity.darkTertiary),
            onTertiary = Color.fromString(entity.darkOnTertiary),
            tertiaryContainer = Color.fromString(entity.darkTertiaryContainer),
            onTertiaryContainer = Color.fromString(entity.darkOnTertiaryContainer),
            error = Color.fromString(entity.darkError),
            onError = Color.fromString(entity.darkOnError),
            errorContainer = Color.fromString(entity.darkErrorContainer),
            onErrorContainer = Color.fromString(entity.darkOnErrorContainer),
            surface = Color.fromString(entity.darkSurface),
            onSurface = Color.fromString(entity.darkOnSurface),
            onSurfaceVariant = Color.fromString(entity.darkOnSurfaceVariant),
            outline = Color.fromString(entity.darkOutline),
            outlineVariant = Color.fromString(entity.darkOutlineVariant),
            inverseSurface = Color.fromString(entity.darkInverseSurface),
            inverseOnSurface = Color.fromString(entity.darkInverseOnSurface),
            inversePrimary = Color.fromString(entity.darkInversePrimary),
            surfaceDim = Color.fromString(entity.darkSurfaceDim),
            surfaceBright = Color.fromString(entity.darkSurfaceBright),
            surfaceContainerLowest = Color.fromString(entity.darkSurfaceContainerLowest),
            surfaceContainerLow = Color.fromString(entity.darkSurfaceContainerLow),
            surfaceContainer = Color.fromString(entity.darkSurfaceContainer),
            surfaceContainerHigh = Color.fromString(entity.darkSurfaceContainerHigh),
            surfaceContainerHighest = Color.fromString(entity.darkSurfaceContainerHighest),
            background = Color.fromString(entity.darkBackground),
            onBackground = Color.fromString(entity.darkOnBackground),
            surfaceTint = Color.fromString(entity.darkSurfaceTint),
            scrim = Color.fromString(entity.darkScrim),
            surfaceVariant = Color.fromString(entity.darkSurfaceVariant),
        ),
    )


    internal fun toEntity(): ColorsEntity {
        return ColorsEntity(
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

    companion object {
        fun fromString(string: String): CorePaletteColor? {
            return when (string) {
                "p" -> Primary
                "s" -> Secondary
                "t" -> Tertiary
                "n" -> Neutral
                "nv" -> NeutralVariant
                "e" -> Error
                else -> null
            }
        }
    }
}

sealed interface Color {
    companion object {
        fun fromString(string: String?): Color? {

            if (string == null) return null
            if (string.startsWith("#")) {
                return StaticColor(string.substring(1).toLongOrNull(16)?.toInt() ?: return null)
            }
            if (string.startsWith("$")) {
                val parts = string.substring(1).split(".").takeIf { it.size == 2 } ?: return null
                val color = CorePaletteColor.fromString(parts[0]) ?: return null
                return ColorRef(
                    color = color,
                    tone = parts[1].toIntOrNull() ?: return null,
                )
            }
            return null
        }
    }
}

data class ColorRef(
    val color: CorePaletteColor,
    val tone: Int,
) : Color {
    override fun toString(): String {
        return "$$color.$tone"
    }
}

@JvmInline
value class StaticColor(val color: Int) : Color {
    override fun toString(): String {
        return "#${color.toUInt().toString(16).padStart(8, '0')}"
    }
}

typealias CorePaletteColorValue = @Serializable(with = ColorIntAsHexSerializer::class) Int

@Serializable
data class CorePalette<out T : Int?>(
    val primary: T,
    val secondary: T,
    val tertiary: T,
    val neutral: T,
    val neutralVariant: T,
    val error: T,
)

val EmptyCorePalette = CorePalette<CorePaletteColorValue?>(null, null, null, null, null, null)

typealias FullCorePalette = CorePalette<CorePaletteColorValue>
typealias PartialCorePalette = CorePalette<CorePaletteColorValue?>

@Serializable
data class ColorScheme<out T : Color?>(
    @Contextual val primary: T,
    @Contextual val onPrimary: T,
    @Contextual val primaryContainer: T,
    @Contextual val onPrimaryContainer: T,
    @Contextual val secondary: T,
    @Contextual val onSecondary: T,
    @Contextual val secondaryContainer: T,
    @Contextual val onSecondaryContainer: T,
    @Contextual val tertiary: T,
    @Contextual val onTertiary: T,
    @Contextual val tertiaryContainer: T,
    @Contextual val onTertiaryContainer: T,
    @Contextual val error: T,
    @Contextual val onError: T,
    @Contextual val errorContainer: T,
    @Contextual val onErrorContainer: T,
    @Contextual val surface: T,
    @Contextual val onSurface: T,
    @Contextual val onSurfaceVariant: T,
    @Contextual val outline: T,
    @Contextual val outlineVariant: T,
    @Contextual val inverseSurface: T,
    @Contextual val inverseOnSurface: T,
    @Contextual val inversePrimary: T,
    @Contextual val surfaceDim: T,
    @Contextual val surfaceBright: T,
    @Contextual val surfaceContainerLowest: T,
    @Contextual val surfaceContainerLow: T,
    @Contextual val surfaceContainer: T,
    @Contextual val surfaceContainerHigh: T,
    @Contextual val surfaceContainerHighest: T,

    @Contextual val background: T,
    @Contextual val onBackground: T,
    @Contextual val surfaceTint: T,
    @Contextual val scrim: T,
    @Contextual val surfaceVariant: T,
)

typealias FullColorScheme = ColorScheme<Color>
typealias PartialColorScheme = ColorScheme<Color?>

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