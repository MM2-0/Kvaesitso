package de.mm20.launcher2.themes.typography

import android.content.Context

data class FontList(
    val builtIn: List<FontFamily>,
    val generic: List<FontFamily>,
    val deviceDefault: List<FontFamily>,
    val system: List<FontFamily>,
)

data class VariableFontAxis(
    val name: String,
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
    val defaultValue: Float,
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
            builtIn = listOf(FontFamily.LauncherDefault()),
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

    fun getFontSettings(font: FontFamily.VariableFontFamily): List<VariableFontAxis> {
        if (font is FontFamily.LauncherDefault) {
            return listOf(
                VariableFontAxis("wdth", "Width", 25f..151f, 1f, 100f),
                VariableFontAxis("ROND", "Roundness", 0f..100f, 1f, 0f),
                VariableFontAxis("GRAD", "Grade", 0f..100f, 1f, 0f),
            )
        }

        return emptyList()
    }
}