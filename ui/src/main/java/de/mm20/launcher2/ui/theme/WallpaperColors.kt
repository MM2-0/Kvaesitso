package de.mm20.launcher2.ui.theme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color

data class WallpaperColors(val primary: Color, val secondary: Color?, val tertiary: Color?) {
    companion object {
        @RequiresApi(Build.VERSION_CODES.O_MR1)
        fun fromPlatformType(colors: android.app.WallpaperColors): WallpaperColors {
            return WallpaperColors(
                Color(colors.primaryColor.toArgb()),
                colors.secondaryColor?.toArgb()?.let { Color(it) },
                colors.tertiaryColor?.toArgb()?.let { Color(it) }
            )
        }
    }
}