package de.mm20.launcher2.weather

import kotlin.math.abs
import de.mm20.launcher2.ktx.pseudoMedianBy

data class DailyForecast(
    val timestamp: Long,
    val minTemp: Double,
    val maxTemp: Double,
    val hourlyForecasts: List<HourlyForecast>,
    val medianHumidity: Double = hourlyForecasts.pseudoMedianBy { it.humidity }!!.humidity,
    val medianWindspeed: Double = hourlyForecasts.pseudoMedianBy { it.windSpeed }!!.windSpeed,
    val medianWindDirection: Double = hourlyForecasts.pseudoMedianBy { it.windDirection }!!.windDirection,
    val medianPrecipitation: Double = hourlyForecasts.pseudoMedianBy { it.precipitation }!!.precipitation,
    val medianPrecipProbability: Int = hourlyForecasts.pseudoMedianBy { it.precipProbability }!!.precipProbability,
    val icon: Int = getAverageIcon(hourlyForecasts)
) {
    companion object {
        private fun getAverageIcon(forecasts: List<HourlyForecast>): Int {
            var clear = 0f
            var clouds = 0f
            var rain = 0f
            var thunder = 0f
            var wind = 0f
            var snow = 0f
            for (f in forecasts) {
                when (f.icon) {
                    HourlyForecast.SHOWERS, HourlyForecast.HAIL -> {
                        rain += 2f
                        clouds += 1f
                    }
                    HourlyForecast.THUNDERSTORM_WITH_RAIN -> {
                        rain += 2f
                        thunder += 5f
                        clouds += 1f
                    }
                    HourlyForecast.THUNDERSTORM -> {
                        thunder += 5f
                        clouds += 1f
                    }
                    HourlyForecast.BROKEN_CLOUDS, HourlyForecast.MOSTLY_CLOUDY -> {
                        clouds += 0.7f
                        clear += 0.3f
                    }
                    HourlyForecast.PARTLY_CLOUDY -> {
                        clouds += 0.3f
                        clear += 0.7f
                    }
                    HourlyForecast.CLOUDY -> {
                        clouds += 1f
                    }
                    HourlyForecast.SNOW -> {
                        snow += 2f
                        clouds += 1f
                    }
                    HourlyForecast.DRIZZLE -> {
                        rain += 1f
                    }
                    HourlyForecast.HEAVY_THUNDERSTORM -> {
                        thunder += 8f
                    }
                    HourlyForecast.HEAVY_THUNDERSTORM_WITH_RAIN -> {
                        thunder += 8f
                        clouds += 1f
                    }
                    HourlyForecast.SLEET -> {
                        rain += 1f
                        snow += 1f
                    }
                    HourlyForecast.STORM -> {
                        wind += 8f
                    }
                    HourlyForecast.WIND -> {
                        wind += 5f
                    }
                    HourlyForecast.CLEAR -> {
                        clear += 1f
                    }
                }
            }
            val pairs = listOf(
                "clear" to clear,
                "clouds" to clouds,
                "rain" to rain,
                "thunder" to thunder,
                "wind" to wind,
                "snow" to snow
            ).sortedByDescending { it.second }
            val first = pairs[0]
            val second = pairs[1]
            when (first.first) {
                "wind" -> return if (first.second / forecasts.size > 6f) HourlyForecast.STORM else HourlyForecast.WIND
                "thunder" -> {
                    val heavy = first.second / forecasts.size > 6f
                    val withRain = second.first == "rain"
                    if (heavy && withRain) return HourlyForecast.HEAVY_THUNDERSTORM_WITH_RAIN
                    if (heavy && !withRain) return HourlyForecast.HEAVY_THUNDERSTORM
                    if (!heavy && withRain) return HourlyForecast.THUNDERSTORM_WITH_RAIN
                    return HourlyForecast.THUNDERSTORM
                }
                "rain" -> {
                    val heavy = first.second / forecasts.size > 0.8f
                    val withSnow =
                        second.first == "snow" && abs(1 - (first.second / second.second)) < 0.2
                    if (withSnow) return HourlyForecast.SLEET
                    if (heavy) return HourlyForecast.SHOWERS
                    return HourlyForecast.DRIZZLE
                }
                "snow" -> {
                    val withRain =
                        second.first == "rain" && abs(1 - (first.second / second.second)) < 0.2
                    if (withRain) return HourlyForecast.SLEET
                    return HourlyForecast.SNOW
                }
                else -> {
                    if (clouds == 0f) return HourlyForecast.CLEAR
                    if (clear == 0f) return HourlyForecast.CLOUDY
                    if (clouds > clear) {
                        if (clear > snow && clear > rain) return HourlyForecast.MOSTLY_CLOUDY
                        if (clear < snow && clear < rain) return HourlyForecast.SLEET
                        if (clear < snow && clear > rain) return HourlyForecast.SNOW
                        if (clear > snow && clear < rain) return HourlyForecast.DRIZZLE
                    }
                    return HourlyForecast.PARTLY_CLOUDY
                }
            }
        }
    }
}