package de.mm20.launcher2.weather

data class Weather2(
        val timestamp: Long,
        val temperature: Double,
        val minTemp: Double,
        val maxTemp: Double,
        val pressure: Double,
        val humidity: Double,
        val icon: Int,
        val condition: String,
        val clouds: String,
        val windSpeed: Double,
        val windDirection: Double,
        val rain: Double,
        val snow: Double,
        val night: Boolean,
        val location: String,
        val provider: String,
        val providerUrl: String
) {

    companion object {
        const val NONE = -1
        const val CLEAR = 0
        const val CLOUDY = 1
        const val COLD = 2
        const val DRIZZLE = 3
        const val HAZE = 4
        const val FOG = 5
        const val HAIL = 6
        const val HEAVY_THUNDERSTORM = 7
        const val HEAVY_THUNDERSTORM_WITH_RAIN = 8
        const val HOT = 9
        const val MOSTLY_CLOUDY = 10
        const val PARTLY_CLOUDY = 11
        const val SHOWERS = 12
        const val SLEET = 13
        const val SNOW = 14
        const val STORM = 15
        const val THUNDERSTORM = 16
        const val THUNDERSTORM_WITH_RAIN = 17
        const val WIND = 18
        const val BROKEN_CLOUDS = 19
    }
}

