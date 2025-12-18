package de.mm20.launcher2.ui.theme.typography.fontfamily

import android.content.Context
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*

fun getDeviceHeadlineFontFamily(context: Context): FontFamily {
    val configResId = context.resources
        .getIdentifier("config_headlineFontFamily", "string", "android")
    
    if (configResId != 0) {
        val fontFamily = context.resources.getString(configResId)

        if (fontFamily.isBlank()) return FontFamily.SansSerif

        return try {
            FontFamily(
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Thin, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.ExtraLight, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Light, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Normal, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Medium, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.SemiBold, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Bold, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.ExtraBold, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Black, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Thin, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.ExtraLight, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Light, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Normal, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Medium, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.SemiBold, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Bold, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.ExtraBold, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Black, style = FontStyle.Italic),
            )
        } catch (e: IllegalArgumentException) {
            FontFamily.SansSerif
        }
    }

    return FontFamily.SansSerif
    
}

fun getDeviceBodyFontFamily(context: Context): FontFamily {
    val configResId = context.resources
        .getIdentifier("config_bodyFontFamily", "string", "android")

    if (configResId != 0) {
        val fontFamily = context.resources.getString(configResId)

        if (fontFamily.isBlank()) return FontFamily.SansSerif

        return try {
            FontFamily(
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Thin, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.ExtraLight, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Light, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Normal, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Medium, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.SemiBold, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Bold, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.ExtraBold, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Black, style = FontStyle.Normal),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Thin, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.ExtraLight, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Light, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Normal, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Medium, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.SemiBold, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Bold, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.ExtraBold, style = FontStyle.Italic),
                Font(DeviceFontFamilyName(fontFamily), weight = FontWeight.Black, style = FontStyle.Italic),
            )
        } catch (e: IllegalArgumentException) {
            FontFamily.SansSerif
        }
    }
    return FontFamily.SansSerif
}