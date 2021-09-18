package de.mm20.launcher2.ui.theme.colors

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

data class ColorSwatch(
    val shade0: Color,
    val shade10: Color,
    val shade50: Color,
    val shade100: Color,
    val shade200: Color,
    val shade300: Color,
    val shade400: Color,
    val shade500: Color,
    val shade600: Color,
    val shade700: Color,
    val shade800: Color,
    val shade900: Color,
    val shade1000: Color,
)

fun colorSwatch(color: Color): ColorSwatch {
    val hsl = floatArrayOf(0f, 0f, 0f)
    val rgb = color.toArgb()
    ColorUtils.RGBToHSL(rgb.red, rgb.green, rgb.blue, hsl)

    return ColorSwatch(
        shade0 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 1f })),
        shade10 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.99f })),
        shade50 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.95f })),
        shade100 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.9f })),
        shade200 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.8f })),
        shade300 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.7f })),
        shade400 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.6f })),
        shade500 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.49f })),
        shade600 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.4f })),
        shade700 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.3f })),
        shade800 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.2f })),
        shade900 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.1f })),
        shade1000 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0f })),
    )
}