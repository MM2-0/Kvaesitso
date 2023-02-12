package de.mm20.launcher2.ui.component.weather

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
        return remember {
            WeatherIconColors(
                sun = Color(context.getColor(R.color.weather_sun)),
                moon = Color(context.getColor(R.color.weather_moon)),
                cloudDark1 = Color(context.getColor(R.color.weather_cloud_dark_1)),
                cloudDark2 = Color(context.getColor(R.color.weather_cloud_dark_2)),
                cloudMedium1 = Color(context.getColor(R.color.weather_cloud_medium_1)),
                cloudMedium2 = Color(context.getColor(R.color.weather_cloud_medium_2)),
                cloudLight1 = Color(context.getColor(R.color.weather_cloud_light_1)),
                cloudLight2 = Color(context.getColor(R.color.weather_cloud_light_2)),
                rain = Color(context.getColor(R.color.weather_rain)),
                snow = Color(context.getColor(R.color.weather_snow)),
                hail = Color(context.getColor(R.color.weather_hail)),
                fog = Color(context.getColor(R.color.weather_fog)),
                wind = Color(context.getColor(R.color.weather_wind)),
                windDark = Color(context.getColor(R.color.weather_wind_dark)),
                lightningBolt = Color(context.getColor(R.color.weather_lightning_bolt)),
                hot = Color(context.getColor(R.color.weather_hot)),
                cold = Color(context.getColor(R.color.weather_cold)),
            )
        }
    }
}