package de.mm20.launcher2.ui.theme.colors

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

data class ColorSwatch(
    val shade100: Color,
    val shade99: Color,
    val shade95: Color,
    val shade90: Color,
    val shade80: Color,
    val shade70: Color,
    val shade60: Color,
    val shade50: Color,
    val shade40: Color,
    val shade30: Color,
    val shade20: Color,
    val shade10: Color,
    val shade0: Color,
)

fun colorSwatch(color: Color): ColorSwatch {
    val hsl = floatArrayOf(0f, 0f, 0f)
    val rgb = color.toArgb()
    ColorUtils.RGBToHSL(rgb.red, rgb.green, rgb.blue, hsl)

    return ColorSwatch(
        shade100 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 1f })),
        shade99 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.99f })),
        shade95 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.95f })),
        shade90 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.9f })),
        shade80 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.8f })),
        shade70 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.7f })),
        shade60 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.6f })),
        shade50 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.49f })),
        shade40 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.4f })),
        shade30 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.3f })),
        shade20 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.2f })),
        shade10 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0.1f })),
        shade0 = Color(ColorUtils.HSLToColor(hsl.also { it[2] = 0f })),
    )
}