package de.mm20.launcher2.ui.component.weather

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import blend.Blend
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.ktx.atTone
import de.mm20.launcher2.ui.locals.LocalDarkTheme


data class WeatherIconColors(
    val sun: Color,
    val moon: Color,
    val cloudDark1: Color,
    val cloudDark2: Color,
    val cloudMedium1: Color,
    val cloudMedium2: Color,
    val cloudLight1: Color,
    val cloudLight2: Color,
    val rain: Color,
    val snow: Color,
    val hail: Color,
    val fog: Color,
    val wind: Color,
    val windDark: Color,
    val lightningBolt: Color,
    val hot: Color,
    val cold: Color,
)

object WeatherIconDefaults {
    @Composable
    fun colors(): WeatherIconColors {
        val context = LocalContext.current

        val themeColor = MaterialTheme.colorScheme.primary
        val darkTheme = LocalDarkTheme.current

        val neutral1 = MaterialTheme.colorScheme.outline
        val neutral2 = MaterialTheme.colorScheme.outline

        return remember(themeColor) {
            WeatherIconColors(
                sun = harmonize(context, 0xFFFFB300.toInt(), themeColor),
                moon = harmonize(context, 0xFF9E9E9E.toInt(), themeColor),
                cloudDark1 = harmonize(context, neutral1.atTone(if (darkTheme) 40 else 30).toArgb(), themeColor),
                cloudDark2 = harmonize(context, neutral1.atTone(if (darkTheme) 30 else 20).toArgb(), themeColor),
                cloudMedium1 = harmonize(context, neutral1.atTone(if (darkTheme) 60 else 50).toArgb(), themeColor),
                cloudMedium2 = harmonize(context, neutral1.atTone(if (darkTheme) 50 else 40).toArgb(), themeColor),
                cloudLight1 = harmonize(context, neutral1.atTone(if (darkTheme) 95 else 85).toArgb(), themeColor),
                cloudLight2 = harmonize(context, neutral1.atTone(if (darkTheme) 85 else 75).toArgb(), themeColor),
                rain = harmonize(context, if (darkTheme) 0xFF64B5F6.toInt() else 0xFF1E88E5.toInt(), themeColor),
                snow = harmonize(context, if (darkTheme) 0xFFF5F5F5.toInt() else 0xFFE0E0E0.toInt(), themeColor),
                hail = harmonize(context, if (darkTheme) 0xFFF5F5F5.toInt() else 0xFFE0E0E0.toInt(), themeColor),
                fog = harmonize(context, neutral1.atTone(if (darkTheme) 95 else 85).toArgb(), themeColor),
                wind = harmonize(context, neutral2.atTone(if (darkTheme) 70 else 75).toArgb(), themeColor),
                windDark = harmonize(context, neutral2.atTone(if (darkTheme) 40 else 45).toArgb(), themeColor),
                lightningBolt = harmonize(context, 0xFFFFB300.toInt(), themeColor),
                hot = harmonize(context, if (darkTheme) 0xFFE57373.toInt() else 0xFFE53935.toInt(), themeColor),
                cold = harmonize(context, if (darkTheme) 0xFF4FC3F7.toInt() else 0xFF039BE5.toInt(), themeColor),
            )
        }
    }
}

private fun harmonize(context: Context, @ColorInt baseColor: Int, themeColor: Color): Color {
    return Color(Blend.harmonize(baseColor, themeColor.toArgb()))
}