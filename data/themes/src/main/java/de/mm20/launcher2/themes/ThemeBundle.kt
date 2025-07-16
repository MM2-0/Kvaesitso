package de.mm20.launcher2.themes

import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.themes.colors.Color
import de.mm20.launcher2.themes.colors.ColorScheme
import de.mm20.launcher2.themes.colors.Colors
import de.mm20.launcher2.themes.colors.CorePalette
import de.mm20.launcher2.themes.colors.DefaultDarkColorScheme
import de.mm20.launcher2.themes.colors.DefaultLightColorScheme
import de.mm20.launcher2.themes.colors.EmptyCorePalette
import de.mm20.launcher2.themes.shapes.Shapes
import de.mm20.launcher2.themes.transparencies.Transparencies
import de.mm20.launcher2.themes.typography.Typography
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.util.UUID

@Serializable
data class ThemeBundle(
    val name: String,
    val author: String? = null,
    val colors: Colors? = null,
    val typography: Typography? = null,
    val shapes: Shapes? = null,
    val transparencies: Transparencies? = null,
    /**
     * The file version, always 2 for the new theme format.
     */
    val version: Int = 2,
) {
    fun toJson(): String {
        return ThemeJson.encodeToString(this)
    }

    companion object {
        fun fromJson(jsonString: String): ThemeBundle? {
            try {
                val jsonElement = ThemeJson.parseToJsonElement(jsonString).jsonObject

                val version = (jsonElement["version"] as? JsonPrimitive)?.intOrNull

                if (version != 2) {
                    return fromLegacyJson(jsonElement)
                }

                return ThemeJson.decodeFromJsonElement<ThemeBundle>(jsonElement).also {
                    Log.d("MM20", "$it")
                }

            } catch (e: SerializationException) {
                CrashReporter.logException(e)
                return null
            } catch (e: IllegalArgumentException) {
                CrashReporter.logException(e)
                return null
            }
        }

        private fun fromLegacyJson(jsonElement: JsonObject): ThemeBundle? {
            try {
                val name = (jsonElement["name"] as? JsonPrimitive)?.contentOrNull ?: return null
                val corePalette = (jsonElement["corePalette"] as? JsonObject)?.let {
                    LegacyThemeJson.decodeFromJsonElement<CorePalette<Int?>>(it)
                }
                val lightColorScheme = (jsonElement["lightColorScheme"] as? JsonObject)?.let {
                    LegacyThemeJson.decodeFromJsonElement<ColorScheme<Color?>>(it)
                }
                val darkColorScheme = (jsonElement["darkColorScheme"] as? JsonObject)?.let {
                    LegacyThemeJson.decodeFromJsonElement<ColorScheme<Color?>>(it)
                }

                val colorScheme = Colors(
                    id = UUID.randomUUID(),
                    name = name,
                    corePalette = corePalette ?: EmptyCorePalette,
                    lightColorScheme = lightColorScheme ?: DefaultLightColorScheme,
                    darkColorScheme = darkColorScheme ?: DefaultDarkColorScheme,
                )
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