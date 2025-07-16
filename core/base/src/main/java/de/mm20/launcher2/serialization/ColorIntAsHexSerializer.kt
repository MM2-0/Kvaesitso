package de.mm20.launcher2.serialization

import androidx.core.graphics.toColorInt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ColorIntAsHexSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(javaClass.canonicalName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Int {
        return decoder.decodeString().toColorInt()
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeString("#" + value.toUInt().toString(16).padStart(8, '0'))
    }
}