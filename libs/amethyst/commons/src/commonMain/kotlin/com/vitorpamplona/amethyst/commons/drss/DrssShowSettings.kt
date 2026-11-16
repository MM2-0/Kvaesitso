/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.drss

import androidx.collection.LruCache
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.drss.show.DrssShowEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * What a drss.io show card shows from the show's settings, the JSON in a `DrssShowEvent`'s content.
 * drss's format is private, so this reads only the plain fields it has been seen to carry, each one
 * optional, and drops the rest (the prompts are kilobytes of instructions to the generator).
 */
@Immutable
data class DrssShowSettings(
    val name: String? = null,
    val picture: String? = null,
    val language: String? = null,
    val voice: String? = null,
    val tags: List<String> = emptyList(),
    val products: List<String> = emptyList(),
    val schedule: List<DrssShowSlot> = emptyList(),
    /** How long the show keeps an episode, in hours (`episode_retention`, `336h`). */
    val retentionHours: Int? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Settings already read, by event id. A show's content runs to 25 KB (most of it the
         * generator's prompts), and a card loses what it remembered whenever it scrolls out of a
         * feed, so each return to the screen would parse it again.
         */
        private val cache = LruCache<HexKey, DrssShowSettings>(64)

        /** The settings in [show]'s content, parsed once per event. */
        fun of(show: DrssShowEvent): DrssShowSettings = cache[show.id] ?: parse(show.content).also { cache.put(show.id, it) }

        /** The settings in [content], or empty settings when it is not a JSON object. */
        fun parse(content: String): DrssShowSettings {
            if (content.isBlank()) return DrssShowSettings()
            val root = runCatching { json.parseToJsonElement(content) }.getOrNull() as? JsonObject ?: return DrssShowSettings()
            return DrssShowSettings(
                name = root.text("name"),
                picture = root.text("picture")?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                language = root.text("language"),
                voice = root.text("voice"),
                tags = root.texts("tags"),
                products = root.texts("products"),
                schedule =
                    (root["schedule"] as? JsonArray)
                        ?.mapNotNull { slot ->
                            val obj = slot as? JsonObject ?: return@mapNotNull null
                            val time = obj.text("time") ?: return@mapNotNull null
                            DrssShowSlot(time = time, zone = obj.text("zone"), part = obj.text("part"))
                        }.orEmpty(),
                retentionHours = root.text("episode_retention")?.let(::hours),
            )
        }

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.contentOrNull
                ?.trim()
                ?.ifBlank { null }

        private fun JsonObject.texts(key: String): List<String> =
            (this[key] as? JsonArray)
                ?.mapNotNull {
                    (it as? JsonPrimitive)
                        ?.takeIf { primitive -> primitive.isString }
                        ?.contentOrNull
                        ?.trim()
                        ?.ifBlank { null }
                }.orEmpty()

        /**
         * [content] with the fields Amethyst edits replaced and every other key kept as it was (the
         * prompts and sources drss's generator reads). A slot keeps the keys it had beyond
         * part, time and zone; a blank text removes its key.
         */
        fun edit(
            content: String,
            name: String,
            language: String?,
            voice: String?,
            tags: List<String>,
            products: List<String>,
            schedule: List<DrssShowSlot>,
            retentionHours: Int?,
        ): String {
            val root = runCatching { json.parseToJsonElement(content) }.getOrNull() as? JsonObject ?: JsonObject(emptyMap())
            val oldSlots = (root["schedule"] as? JsonArray).orEmpty()
            val fields = root.toMutableMap()

            fun put(
                key: String,
                value: String?,
            ) {
                val text = value?.trim()
                if (text.isNullOrEmpty()) fields.remove(key) else fields[key] = JsonPrimitive(text)
            }

            put("name", name)
            put("language", language)
            put("voice", voice)
            fields["tags"] = JsonArray(tags.map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) })
            fields["products"] = JsonArray(products.map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) })
            fields["schedule"] =
                JsonArray(
                    schedule.mapIndexed { index, slot ->
                        val slotFields = ((oldSlots.getOrNull(index) as? JsonObject) ?: JsonObject(emptyMap())).toMutableMap()
                        slotFields["time"] = JsonPrimitive(slot.time.trim())
                        slot.zone
                            ?.trim()
                            ?.ifEmpty { null }
                            ?.let { slotFields["zone"] = JsonPrimitive(it) } ?: slotFields.remove("zone")
                        slot.part
                            ?.trim()
                            ?.ifEmpty { null }
                            ?.let { slotFields["part"] = JsonPrimitive(it) } ?: slotFields.remove("part")
                        JsonObject(slotFields)
                    },
                )
            if (retentionHours != null && retentionHours > 0) fields["episode_retention"] = JsonPrimitive("${retentionHours}h") else fields.remove("episode_retention")
            return JsonObject(fields).toString()
        }

        /** `336h` as 336; anything else as null. */
        private fun hours(value: String): Int? = value.removeSuffix("h").toIntOrNull()?.takeIf { value.endsWith("h") && it > 0 }
    }
}

/** When a drss show publishes: a 24-hour [time] in [zone], with the [part] of the day it names. */
@Immutable
data class DrssShowSlot(
    val time: String,
    val zone: String? = null,
    val part: String? = null,
)
