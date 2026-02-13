package de.mm20.launcher2.weather

data class DailyForecast(
    val timestamp: Long,
    val minTemp: Double,
    val maxTemp: Double,
    val hourlyForecasts: List<Forecast>,
    val icon: Int = getAverageIcon(hourlyForecasts)
) {
    companion object {
        private fun getAverageIcon(forecasts: List<Forecast>): Int {
            if (forecasts.size == 1) {
                return forecasts[0].icon
            }

            var clear = 0f
            var clouds = 0f
            var rain = 0f
            var thunder = 0f
            var wind = 0f
            var snow = 0f
            var hail = 0f
            var precipitation = 0f
            var total = 0f

            for (fc in forecasts) {
                val f = if (fc.night) 0.8f else 1f
                when (fc.icon) {
                    Forecast.CLEAR -> clear += f
                    Forecast.OVERCAST -> clouds += f
                    Forecast.PARTLY_CLOUDY -> {
                        clouds += f * 0.5f
                        clear += f * 0.5f
                    }

                    Forecast.RAIN -> {
                        rain += f
                        clouds += f
                        precipitation += f
                    }

                    Forecast.HEAVY_RAIN -> {
                        rain += f * 1.5f
                        clouds += f
                        precipitation += f
                    }

                    Forecast.LIGHT_RAIN -> {
                        rain += f * 0.75f
                        clouds += f
                        precipitation += f
                    }

                    Forecast.SNOW -> {
                        snow += f
                        clouds += f
                        precipitation += f
                    }

                    Forecast.SLEET -> {
                        snow += f * 0.5f
                        rain += f * 0.5f
                        clouds += f
                        precipitation += f
                    }

                    Forecast.HAIL -> {
                        hail += f
                        clouds += f
                        precipitation += f
                    }

                    Forecast.THUNDER -> {
                        thunder += f
                        clouds += f
                    }

                    Forecast.THUNDERSTORM -> {
                        thunder += f
                        rain += f * 1.5f
                        clouds += f
                        precipitation += f
                    }

                    Forecast.WIND -> {
                        wind += f
                        clouds += f
                    }


                }
                total += f
            }

            if (wind / total >= 0.05f) {
                return Forecast.WIND
            }


            if (precipitation / total >= 0.3f) {
                return when {
                    hail > snow && hail > rain -> Forecast.HAIL
                    snow > rain && snow / rain > 2f -> Forecast.SNOW
                    snow / rain <= 2f && rain / snow <= 2f -> Forecast.SLEET
                    thunder > 0f -> Forecast.THUNDERSTORM
                    (rain + snow + hail) / precipitation >= 1.25f -> Forecast.HEAVY_RAIN
                    (rain + snow + hail) / precipitation <= 0.8725f -> Forecast.LIGHT_RAIN
                    else -> Forecast.RAIN
                }
            }

            if (thunder / total >= 0.05f) {
                return Forecast.THUNDER
            }

            if (clear / clouds < 0.2f) {
                return Forecast.OVERCAST
            }

            if (clouds / clear < 0.1f) {
                return Forecast.CLEAR
            }

            return Forecast.PARTLY_CLOUDY

        }
    }
}