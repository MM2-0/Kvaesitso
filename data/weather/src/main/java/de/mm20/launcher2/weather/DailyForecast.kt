package de.mm20.launcher2.weather

import kotlin.math.abs

data class DailyForecast(
        val timestamp: Long,
        val minTemp: Double,
        val maxTemp: Double,
        val hourlyForecasts: List<Forecast>,
        val icon: Int = getAverageIcon(hourlyForecasts)
) {
    companion object {
        private fun getAverageIcon(forecasts: List<Forecast>): Int {
            var clear = 0f
            var clouds = 0f
            var rain = 0f
            var thunder = 0f
            var wind = 0f
            var snow = 0f
            for (f in forecasts) {
                when (f.icon) {
                    Forecast.SHOWERS, Forecast.HAIL -> {
                        rain += 2f
                        clouds += 1f
                    }
                    Forecast.THUNDERSTORM_WITH_RAIN -> {
                        rain += 2f
                        thunder += 5f
                        clouds += 1f
                    }
                    Forecast.THUNDERSTORM -> {
                        thunder += 5f
                        clouds += 1f
                    }
                    Forecast.BROKEN_CLOUDS, Forecast.MOSTLY_CLOUDY -> {
                        clouds += 0.7f
                        clear += 0.3f
                    }
                    Forecast.PARTLY_CLOUDY -> {
                        clouds += 0.3f
                        clear += 0.7f
                    }
                    Forecast.CLOUDY -> {
                        clouds += 1f
                    }
                    Forecast.SNOW -> {
                        snow += 2f
                        clouds += 1f
                    }
                    Forecast.DRIZZLE -> {
                        rain += 1f
                    }
                    Forecast.HEAVY_THUNDERSTORM -> {
                        thunder += 8f
                    }
                    Forecast.HEAVY_THUNDERSTORM_WITH_RAIN -> {
                        thunder += 8f
                        clouds += 1f
                    }
                    Forecast.SLEET -> {
                        rain += 1f
                        snow += 1f
                    }
                    Forecast.STORM -> {
                        wind += 8f
                    }
                    Forecast.WIND -> {
                        wind += 5f
                    }
                    Forecast.CLEAR -> {
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
                "wind" -> return if (first.second / forecasts.size > 6f) Forecast.STORM else Forecast.WIND
                "thunder" -> {
                    val heavy = first.second / forecasts.size > 6f
                    val withRain = second.first == "rain"
                    if (heavy && withRain) return Forecast.HEAVY_THUNDERSTORM_WITH_RAIN
                    if (heavy && !withRain) return Forecast.HEAVY_THUNDERSTORM
                    if (!heavy && withRain) return Forecast.THUNDERSTORM_WITH_RAIN
                    return Forecast.THUNDERSTORM
                }
                "rain" -> {
                    val heavy = first.second / forecasts.size > 0.8f
                    val withSnow = second.first == "snow" && abs(1 - (first.second / second.second)) < 0.2
                    if (withSnow) return Forecast.SLEET
                    if (heavy) return Forecast.SHOWERS
                    return Forecast.DRIZZLE
                }
                "snow" -> {
                    val withRain = second.first == "rain" && abs(1 - (first.second / second.second)) < 0.2
                    if (withRain) return Forecast.SLEET
                    return Forecast.SNOW
                }
                else -> {
                    if (clouds == 0f) return Forecast.CLEAR
                    if (clear == 0f) return Forecast.CLOUDY
                    if (clouds > clear) {
                        if (clear > snow && clear > rain) return Forecast.MOSTLY_CLOUDY
                        if (clear < snow && clear < rain) return Forecast.SLEET
                        if (clear < snow && clear > rain) return Forecast.SNOW
                        if (clear > snow && clear < rain) return Forecast.DRIZZLE
                    }
                    return Forecast.PARTLY_CLOUDY
                }
            }
        }
    }
}