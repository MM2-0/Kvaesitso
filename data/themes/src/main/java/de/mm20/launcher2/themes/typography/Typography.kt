package de.mm20.launcher2.themes.typography

import de.mm20.launcher2.database.entities.TypographyEntity
import de.mm20.launcher2.serialization.UUIDSerializer
import de.mm20.launcher2.themes.FontWeightSerializer
import de.mm20.launcher2.themes.ThemeJson
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Typography(
    @Serializable(with = UUIDSerializer::class) val id: UUID = UUID.randomUUID(),
    val builtIn: Boolean = false,
    val name: String,

    /**
     * Map of font families used in this typography.
     * `null` refers to the default font (sans-serif).
     */
    val fonts: Map<String, FontFamily?> = mapOf("brand" to null, "plain" to null),

    val styles: TextStyles<@Contextual FontWeight.Absolute?> = TextStyles(),
    val emphasizedStyles: TextStyles<FontWeight?> = TextStyles(),
) {
    internal constructor(entity: TypographyEntity) : this(
        id = entity.id,
        builtIn = false,
        name = entity.name,
        fonts = entity.fonts?.let { ThemeJson.decodeFromString(it) } ?: mapOf(
            "brand" to null,
            "plain" to null
        ),
        styles = TextStyles(
            bodySmall = TextStyle.fromString(entity.bodySmall),
            bodyMedium = TextStyle.fromString(entity.bodyMedium),
            bodyLarge = TextStyle.fromString(entity.bodyLarge),
            labelSmall = TextStyle.fromString(entity.labelSmall),
            labelMedium = TextStyle.fromString(entity.labelMedium),
            labelLarge = TextStyle.fromString(entity.labelLarge),
            titleSmall = TextStyle.fromString(entity.titleSmall),
            titleMedium = TextStyle.fromString(entity.titleMedium),
            titleLarge = TextStyle.fromString(entity.titleLarge),
            headlineSmall = TextStyle.fromString(entity.headlineSmall),
            headlineMedium = TextStyle.fromString(entity.headlineMedium),
            headlineLarge = TextStyle.fromString(entity.headlineLarge),
            displaySmall = TextStyle.fromString(entity.displaySmall),
            displayMedium = TextStyle.fromString(entity.displayMedium),
            displayLarge = TextStyle.fromString(entity.displayLarge)
        ),
        emphasizedStyles = TextStyles(
            bodySmall = TextStyle.fromString(entity.emphasizedBodySmall),
            bodyMedium = TextStyle.fromString(entity.emphasizedBodyMedium),
            bodyLarge = TextStyle.fromString(entity.emphasizedBodyLarge),
            labelSmall = TextStyle.fromString(entity.emphasizedLabelSmall),
            labelMedium = TextStyle.fromString(entity.emphasizedLabelMedium),
            labelLarge = TextStyle.fromString(entity.emphasizedLabelLarge),
            titleSmall = TextStyle.fromString(entity.emphasizedTitleSmall),
            titleMedium = TextStyle.fromString(entity.emphasizedTitleMedium),
            titleLarge = TextStyle.fromString(entity.emphasizedTitleLarge),
            headlineSmall = TextStyle.fromString(entity.emphasizedHeadlineSmall),
            headlineMedium = TextStyle.fromString(entity.emphasizedHeadlineMedium),
            headlineLarge = TextStyle.fromString(entity.emphasizedHeadlineLarge),
            displaySmall = TextStyle.fromString(entity.emphasizedDisplaySmall),
            displayMedium = TextStyle.fromString(entity.emphasizedDisplayMedium),
            displayLarge = TextStyle.fromString(entity.emphasizedDisplayLarge)
        )
    )

    internal fun toEntity(): TypographyEntity {
        return TypographyEntity(
            id = id,
            name = name,
            fonts = ThemeJson.encodeToString(fonts),
            displayLarge = styles.displayLarge?.toString(),
            displayMedium = styles.displayMedium?.toString(),
            displaySmall = styles.displaySmall?.toString(),
            headlineLarge = styles.headlineLarge?.toString(),
            headlineMedium = styles.headlineMedium?.toString(),
            headlineSmall = styles.headlineSmall?.toString(),
            titleLarge = styles.titleLarge?.toString(),
            titleMedium = styles.titleMedium?.toString(),
            titleSmall = styles.titleSmall?.toString(),
            bodyLarge = styles.bodyLarge?.toString(),
            bodyMedium = styles.bodyMedium?.toString(),
            bodySmall = styles.bodySmall?.toString(),
            labelLarge = styles.labelLarge?.toString(),
            labelMedium = styles.labelMedium?.toString(),
            labelSmall = styles.labelSmall?.toString(),
            emphasizedDisplayLarge = emphasizedStyles.displayLarge?.toString(),
            emphasizedDisplayMedium = emphasizedStyles.displayMedium?.toString(),
            emphasizedDisplaySmall = emphasizedStyles.displaySmall?.toString(),
            emphasizedHeadlineLarge = emphasizedStyles.headlineLarge?.toString(),
            emphasizedHeadlineMedium = emphasizedStyles.headlineMedium?.toString(),
            emphasizedHeadlineSmall = emphasizedStyles.headlineSmall?.toString(),
            emphasizedTitleLarge = emphasizedStyles.titleLarge?.toString(),
            emphasizedTitleMedium = emphasizedStyles.titleMedium?.toString(),
            emphasizedTitleSmall = emphasizedStyles.titleSmall?.toString(),
            emphasizedBodyLarge = emphasizedStyles.bodyLarge?.toString(),
            emphasizedBodyMedium = emphasizedStyles.bodyMedium?.toString(),
            emphasizedBodySmall = emphasizedStyles.bodySmall?.toString(),
            emphasizedLabelLarge = emphasizedStyles.labelLarge?.toString(),
            emphasizedLabelMedium = emphasizedStyles.labelMedium?.toString(),
            emphasizedLabelSmall = emphasizedStyles.labelSmall?.toString(),
        )
    }
}

@Serializable
data class TextStyles<out W : FontWeight?>(
    val bodySmall: TextStyle<W>? = null,
    val bodyMedium: TextStyle<W>? = null,
    val bodyLarge: TextStyle<W>? = null,
    val labelSmall: TextStyle<W>? = null,
    val labelMedium: TextStyle<W>? = null,
    val labelLarge: TextStyle<W>? = null,
    val titleSmall: TextStyle<W>? = null,
    val titleMedium: TextStyle<W>? = null,
    val titleLarge: TextStyle<W>? = null,
    val headlineSmall: TextStyle<W>? = null,
    val headlineMedium: TextStyle<W>? = null,
    val headlineLarge: TextStyle<W>? = null,
    val displaySmall: TextStyle<W>? = null,
    val displayMedium: TextStyle<W>? = null,
    val displayLarge: TextStyle<W>? = null,
)

@Serializable
data class TextStyle<out W : FontWeight?>(
    /**
     * Index of the font family to use for this text style.
     */
    val fontFamily: String? = null,
    /**
     * The font size in sp.
     */
    val fontSize: Int? = null,
    /**
     * The font weight, e.g. 400 for normal, 700 for bold.
     */
    @Contextual val fontWeight: W? = null,
    /**
     * The line height in em.
     */
    val lineHeight: Float? = null,
    /**
     * The letter spacing in em.
     */
    val letterSpacing: Float? = null,
) {
    /**
     * Secondary constructor that uses absolute units for line height and letter spacing.
     */
    constructor(
        fontFamily: String?,
        fontSize: Int,
        fontWeight: W?,
        lineHeight: Int?,
        letterSpacing: Float?,
    ) : this(
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight?.toFloat()?.div(fontSize),
        letterSpacing = letterSpacing?.div(fontSize)
    )


    override fun toString(): String {
        return ThemeJson.encodeToString<TextStyle<FontWeight?>>(this)
    }

    companion object {
        fun <T : FontWeight> fromString(string: String?): TextStyle<T>? {
            if (string.isNullOrEmpty()) return null
            return ThemeJson.decodeFromString<TextStyle<FontWeight>>(string) as TextStyle<T>
        }
    }
}

@Serializable
sealed interface FontFamily {
    @Serializable
    @SerialName("launcher_default")
    data object LauncherDefault : FontFamily

    @Serializable
    @SerialName("device_headline")
    data object DeviceHeadline : FontFamily

    @Serializable
    @SerialName("device_body")
    data object DeviceBody : FontFamily

    @Serializable
    @SerialName("sans-serif")
    data object SansSerif : FontFamily

    @Serializable
    @SerialName("serif")
    data object Serif : FontFamily

    @Serializable
    @SerialName("monospace")
    data object Monospace : FontFamily

    @Serializable
    @SerialName("system")
    data class System(val name: String) : FontFamily
}

@Serializable(with = FontWeightSerializer::class)
sealed interface FontWeight {
    /**
     * Absolute font weight, e.g. 400 for normal, 700 for bold.
     */
    @JvmInline
    @Serializable
    value class Absolute(val weight: Int) : FontWeight

    /**
     * Relative font weight, in relation to another font style.
     * This is used for emphasized styles, i.e. if the base style has a weight of 400,
     * the emphasized style with a relative weight of 100 will have a weight of 500,
     */
    @JvmInline
    @Serializable
    value class Relative(val relativeWeight: Int) : FontWeight
}