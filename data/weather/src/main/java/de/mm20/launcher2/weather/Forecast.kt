package de.mm20.launcher2.weather

import de.mm20.launcher2.database.entities.ForecastEntity
import de.mm20.launcher2.weather.Forecast.Companion.BROKEN_CLOUDS
import de.mm20.launcher2.weather.Forecast.Companion.CLEAR
import de.mm20.launcher2.weather.Forecast.Companion.CLOUDY
import de.mm20.launcher2.weather.Forecast.Companion.COLD
import de.mm20.launcher2.weather.Forecast.Companion.DRIZZLE
import de.mm20.launcher2.weather.Forecast.Companion.FOG
import de.mm20.launcher2.weather.Forecast.Companion.HAIL
import de.mm20.launcher2.weather.Forecast.Companion.HAZE
import de.mm20.launcher2.weather.Forecast.Companion.HEAVY_THUNDERSTORM
import de.mm20.launcher2.weather.Forecast.Companion.HEAVY_THUNDERSTORM_WITH_RAIN
import de.mm20.launcher2.weather.Forecast.Companion.HOT
import de.mm20.launcher2.weather.Forecast.Companion.MOSTLY_CLOUDY
import de.mm20.launcher2.weather.Forecast.Companion.NONE
import de.mm20.launcher2.weather.Forecast.Companion.PARTLY_CLOUDY
import de.mm20.launcher2.weather.Forecast.Companion.SHOWERS
import de.mm20.launcher2.weather.Forecast.Companion.SLEET
import de.mm20.launcher2.weather.Forecast.Companion.SNOW
import de.mm20.launcher2.weather.Forecast.Companion.STORM
import de.mm20.launcher2.weather.Forecast.Companion.THUNDERSTORM
import de.mm20.launcher2.weather.Forecast.Companion.THUNDERSTORM_WITH_RAIN
import de.mm20.launcher2.weather.Forecast.Companion.WIND

data class Forecast(
        val timestamp: Long,
        /** The temperature, in Kelvin **/
        val temperature: Double,
        /** The min temperature, in Kelvin **/
        val minTemp: Double? = null,
        /** The max temperature, in Kelvin **/
        val maxTemp: Double? = null,
        /** The temperature, in hPa **/
        val pressure: Double? = null,
        /** The temperature, in percent **/
        val humidity: Double? = null,
        /** The icon, one of [NONE], [CLEAR], [CLOUDY], [COLD], [DRIZZLE], [HAZE], [FOG],
         *  [HAIL], [HEAVY_THUNDERSTORM], [HEAVY_THUNDERSTORM_WITH_RAIN], [HOT], [MOSTLY_CLOUDY],
         *  [PARTLY_CLOUDY], [SHOWERS], [SLEET], [SNOW], [STORM], [THUNDERSTORM],
         *  [THUNDERSTORM_WITH_RAIN], [WIND], [BROKEN_CLOUDS]**/
        val icon: Int,
        /** A text describing the current weather condition **/
        val condition: String,
        /** The clouds, percentage **/
        val clouds: Int? = null,
        /** Wind speed, in m/s **/
        val windSpeed: Double? = null,
        /** wind direction, in degrees **/
        val windDirection: Double? = null,
        /** rain, in mm per hour **/
        val precipitation: Double? = null,
        /** whether this forecast is during night time (whether a moon icon should be used instead of sun) **/
        val night: Boolean = false,
        /** Location string **/
        val location: String,
        /** Provider name **/
        val provider: String,
        /** Url to the provider and more weather information **/
        val providerUrl: String = "",
        /** Rain probability, in percent [0..100]. null if not available **/
        val precipProbability: Int? = null,
        /** Timestamp (in millis) when when this forecast was created **/
        val updateTime: Long
) {
    fun toDatabaseEntity(): ForecastEntity {
        return ForecastEntity(
                timestamp = timestamp,
                clouds = clouds ?: -1,
                condition = condition,
                humidity = humidity ?: -1.0,
                icon = icon,
                location = location,
                maxTemp = maxTemp ?: -1.0,
                minTemp = minTemp ?: -1.0,
                night = night,
                pressure = pressure ?: -1.0,
                provider = provider,
                providerUrl = providerUrl,
                precipitation = precipitation ?: -1.0,
                precipProbability = precipProbability ?: -1,
                temperature = temperature,
                updateTime = updateTime,
                windDirection = windDirection ?: -1.0,
                windSpeed = windSpeed ?: -1.0,
                snow = -1.0,
                snowProbability = -1
        )
    }

    constructor(entity: ForecastEntity) : this(
            timestamp = entity.timestamp,
            clouds = entity.clouds.takeIf { it >= 0 },
            condition = entity.condition,
            humidity = entity.humidity.takeIf { it >= 0.0 },
            icon = entity.icon,
            location = entity.location,
            maxTemp = entity.maxTemp.takeIf { it >= 0.0 },
            minTemp = entity.minTemp.takeIf { it >= 0.0 },
            night = entity.night,
            pressure = entity.pressure.takeIf { it >= 0.0 },
            provider = entity.provider,
            providerUrl = entity.providerUrl,
            precipitation = entity.precipitation.takeIf { it >= 0.0 },
            precipProbability = entity.precipProbability.takeIf { it >= 0 },
            temperature = entity.temperature,
            updateTime = entity.updateTime,
            windDirection = entity.windDirection.takeIf { it >= 0.0 },
            windSpeed = entity.windSpeed.takeIf { it >= 0.0 },
    )

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