package de.mm20.launcher2.themes

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

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

internal object ColorSerializer: KSerializer<Color> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ColorSerializer", PrimitiveKind.STRING)

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

internal object ShapeSerializer: KSerializer<Shape> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ShapeSerializer", PrimitiveKind.STRING)

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