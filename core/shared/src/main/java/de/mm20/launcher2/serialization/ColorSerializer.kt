package de.mm20.launcher2.serialization

import android.graphics.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ColorSerializer: KSerializer<Color> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(javaClass.canonicalName!!, PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Color {
        return Color.valueOf(decoder.decodeInt())
    }

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeInt(value.toArgb())
    }
}