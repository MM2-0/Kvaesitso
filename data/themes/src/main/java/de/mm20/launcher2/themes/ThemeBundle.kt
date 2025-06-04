package de.mm20.launcher2.themes

import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.serialization.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

@Serializable
data class ThemeBundle(
    val name: String,
    val author: String? = null,
    val colors: Colors? = null,
    val shapes: Shapes? = null,
    /**
     * The file version, always 2 for the new theme format.
     */
    val version: Int = 2,
) {
    fun toJson(): String {
        return Json.Lenient.encodeToString(this)
    }

    companion object {
        fun fromJson(jsonString: String): ThemeBundle? {
            try {
                val jsonElement = Json.Lenient.parseToJsonElement(jsonString).jsonObject

                val version = (jsonElement.get("version") as? JsonPrimitive)?.intOrNull

                if (version != 2) {
                    return fromLegacyJson(jsonElement)
                }

                return Json.Lenient.decodeFromJsonElement(jsonElement)

            } catch (e: SerializationException) {
                CrashReporter.logException(e)
                return null
            } catch (e: IllegalArgumentException) {
                CrashReporter.logException(e)
                return null
            }
        }

        private fun fromLegacyJson(jsonElement: JsonElement): ThemeBundle? {
            try {
                val colorScheme: Colors = LegacyThemeJson.decodeFromJsonElement(jsonElement)
                return ThemeBundle(
                    name = colorScheme.name,
                    author = "",
                    colors = colorScheme,
                    shapes = null,
                    version = 2,
                )
            } catch (e: SerializationException) {
                CrashReporter.logException(e)
                return null
            }

        }
    }
}