package de.mm20.launcher2.themes

import de.mm20.launcher2.themes.colors.Color
import de.mm20.launcher2.themes.colors.StaticColor
import de.mm20.launcher2.themes.shapes.Shape
import de.mm20.launcher2.themes.typography.FontWeight
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

internal val module = SerializersModule {
    contextual(Color::class, ColorSerializer)

}

val ThemeJson = Json {
    serializersModule = module
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}

internal object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ColorSerializer", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Color
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Color {
        val stringValue = decoder.decodeString()
        return Color.fromString(stringValue) ?: StaticColor(0xFF000000.toInt())
    }
}

internal object ShapeSerializer : KSerializer<Shape> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ShapeSerializer", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Shape
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Shape {
        return Shape.fromString(decoder.decodeString())!!
    }
}

internal object FontWeightSerializer : KSerializer<FontWeight?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FontWeightSerializer", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: FontWeight?
    ) {
        if (value is FontWeight.Absolute) {
            encoder.encodeString(value.weight.toString())
        } else if (value is FontWeight.Relative) {
            encoder.encodeString(
                (if (value.relativeWeight >= 0) "+" else "-") + value.relativeWeight.toString()
            )
        }
    }

    override fun deserialize(decoder: Decoder): FontWeight? {
        val str = decoder.decodeString()

        if (str.isBlank()) return null

        return when {
            str.startsWith("+") -> FontWeight.Relative(str.substring(1).toInt())
            str.startsWith("-") -> FontWeight.Relative(-str.substring(1).toInt())
            else -> FontWeight.Absolute(str.toInt())
        }
    }
}