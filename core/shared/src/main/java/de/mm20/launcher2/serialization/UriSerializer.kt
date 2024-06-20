package de.mm20.launcher2.serialization

import android.net.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UriSerializer : KSerializer<Uri> {
    override val descriptor = PrimitiveSerialDescriptor(javaClass.canonicalName!!, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) {
        // We use millis here for backwards compatibility in LocationSerializer
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Uri {
        return Uri.parse(decoder.decodeString())
    }
}