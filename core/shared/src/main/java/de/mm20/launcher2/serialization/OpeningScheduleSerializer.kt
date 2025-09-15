package de.mm20.launcher2.serialization

import android.util.Log
import de.mm20.launcher2.search.location.OpeningSchedule
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

object OpeningScheduleSerializer : KSerializer<OpeningSchedule> {
    override val descriptor = PolymorphicSerializer(OpeningSchedule::class).descriptor

    override fun serialize(encoder: Encoder, value: OpeningSchedule) {
        when (value) {
            is OpeningSchedule.TwentyFourSeven -> encoder.encodeString("twentyFourSeven")

            is OpeningSchedule.Hours -> encoder.encodeSerializableValue(
                OpeningSchedule.Hours.serializer(),
                value
            )
        }
    }

    override fun deserialize(decoder: Decoder): OpeningSchedule {
        decoder as JsonDecoder
        val jsonElement = decoder.decodeJsonElement()

        if ((jsonElement as? JsonPrimitive)?.content == "twentyFourSeven") {
            return OpeningSchedule.TwentyFourSeven
        }

        if ("openingHours" in jsonElement.jsonObject.keys &&
            /* backwards compatibility */ !jsonElement.jsonObject["openingHours"]!!.jsonArray.isEmpty()
        ) {
            return try {
                decoder.json.decodeFromJsonElement<OpeningSchedule.Hours>(
                    jsonElement
                )
            } catch (e: SerializationException) {
                Log.e("MM20", "Failed to deserialize OpeningSchedule.Hours", e)
                return OpeningSchedule.Hours(openingHours = emptySet())
            }
        }

        // fallback in case we receive data which was serialized before introducing OpeningScheduleSerializer.
        // here openingHours is empty, in which case it has to be 24/7, else it would indicate corrupted data.
        return OpeningSchedule.TwentyFourSeven
    }
}