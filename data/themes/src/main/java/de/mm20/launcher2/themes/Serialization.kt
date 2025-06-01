package de.mm20.launcher2.themes

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal class ColorSerializer: KSerializer<Color> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ColorSerializer", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Color
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Color {
        TODO("Not yet implemented")
    }

}

internal class ShapeSerializer: KSerializer<Shape> {
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