package de.mm20.launcher2.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalTime

object LocalTimeSerializer: KSerializer<LocalTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeLong(value.toNanoOfDay())
    }

    override fun deserialize(decoder: Decoder): LocalTime {
        return LocalTime.ofNanoOfDay(decoder.decodeLong())
    }
}