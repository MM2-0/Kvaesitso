package de.mm20.launcher2.ui.component.weather

import android.content.Context
import androidx.annotation.ColorRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import blend.Blend
import de.mm20.launcher2.ui.R


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

        return remember(themeColor) {
            WeatherIconColors(
                sun = harmonize(context, R.color.weather_sun, themeColor),
                moon = harmonize(context, R.color.weather_moon, themeColor),
                cloudDark1 = harmonize(context, R.color.weather_cloud_dark_1, themeColor),
                cloudDark2 = harmonize(context, R.color.weather_cloud_dark_2, themeColor),
                cloudMedium1 = harmonize(context, R.color.weather_cloud_medium_1, themeColor),
                cloudMedium2 = harmonize(context, R.color.weather_cloud_medium_2, themeColor),
                cloudLight1 = harmonize(context, R.color.weather_cloud_light_1, themeColor),
                cloudLight2 = harmonize(context, R.color.weather_cloud_light_2, themeColor),
                rain = harmonize(context, R.color.weather_rain, themeColor),
                snow = harmonize(context, R.color.weather_snow, themeColor),
                hail = harmonize(context, R.color.weather_hail, themeColor),
                fog = harmonize(context, R.color.weather_fog, themeColor),
                wind = harmonize(context, R.color.weather_wind, themeColor),
                windDark = harmonize(context, R.color.weather_wind_dark, themeColor),
                lightningBolt = harmonize(context, R.color.weather_lightning_bolt, themeColor),
                hot = harmonize(context, R.color.weather_hot, themeColor),
                cold = harmonize(context, R.color.weather_cold, themeColor),
            )
        }
    }
}

private fun harmonize(context: Context, @ColorRes baseColor: Int, themeColor: Color): Color {
    return Color(Blend.harmonize(context.getColor(baseColor), themeColor.toArgb()))
}