package de.mm20.launcher2.themes.typography

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.SystemFonts
import android.os.Build
import android.util.Log
import androidx.core.graphics.TypefaceCompat

data class FontList(
    val builtIn: List<FontFamily>,
    val generic: List<FontFamily>,
    val deviceDefault: List<FontFamily>,
    val system: List<FontFamily>,
)

class FontManager(
    private val context: Context
) {
    fun getInstalledFonts(): FontList {
        val deviceHeadlineResId = context.resources
            .getIdentifier("config_headlineFontFamily", "string", "android")
        val deviceBodyResId = context.resources
            .getIdentifier("config_bodyFontFamily", "string", "android")

        val deviceHeadlineExists = deviceHeadlineResId != 0 &&
            context.resources.getString(deviceHeadlineResId).isNotBlank()

        val deviceBodyExists = deviceBodyResId != 0 &&
            context.resources.getString(deviceBodyResId).isNotBlank()
        return FontList(
            builtIn = listOf(FontFamily.LauncherDefault, FontFamily.LauncherDefaultRound),
            generic = listOf(
                FontFamily.SansSerif,
                FontFamily.Serif,
                FontFamily.Monospace,
            ),
            deviceDefault = listOfNotNull(
                if (deviceHeadlineExists) FontFamily.DeviceHeadline else null,
                if (deviceBodyExists) FontFamily.DeviceBody else null,
            ),
            system = emptyList(),
        )
    }
}