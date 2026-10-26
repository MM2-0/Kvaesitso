package de.mm20.launcher2.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Year

object YearSerializer: KSerializer<Year> {
    override val descriptor = PrimitiveSerialDescriptor(javaClass.canonicalName!!, PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Year) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): Year {
        return Year.of(decoder.decodeInt())
    }
}