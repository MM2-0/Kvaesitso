package de.mm20.launcher2.weather

import de.mm20.launcher2.database.entities.ForecastEntity
import de.mm20.launcher2.weather.HourlyForecast.Companion.BROKEN_CLOUDS
import de.mm20.launcher2.weather.HourlyForecast.Companion.CLEAR
import de.mm20.launcher2.weather.HourlyForecast.Companion.CLOUDY
import de.mm20.launcher2.weather.HourlyForecast.Companion.COLD
import de.mm20.launcher2.weather.HourlyForecast.Companion.DRIZZLE
import de.mm20.launcher2.weather.HourlyForecast.Companion.FOG
import de.mm20.launcher2.weather.HourlyForecast.Companion.HAIL
import de.mm20.launcher2.weather.HourlyForecast.Companion.HAZE
import de.mm20.launcher2.weather.HourlyForecast.Companion.HEAVY_THUNDERSTORM
import de.mm20.launcher2.weather.HourlyForecast.Companion.HEAVY_THUNDERSTORM_WITH_RAIN
import de.mm20.launcher2.weather.HourlyForecast.Companion.HOT
import de.mm20.launcher2.weather.HourlyForecast.Companion.MOSTLY_CLOUDY
import de.mm20.launcher2.weather.HourlyForecast.Companion.NONE
import de.mm20.launcher2.weather.HourlyForecast.Companion.PARTLY_CLOUDY
import de.mm20.launcher2.weather.HourlyForecast.Companion.SHOWERS
import de.mm20.launcher2.weather.HourlyForecast.Companion.SLEET
import de.mm20.launcher2.weather.HourlyForecast.Companion.SNOW
import de.mm20.launcher2.weather.HourlyForecast.Companion.STORM
import de.mm20.launcher2.weather.HourlyForecast.Companion.THUNDERSTORM
import de.mm20.launcher2.weather.HourlyForecast.Companion.THUNDERSTORM_WITH_RAIN
import de.mm20.launcher2.weather.HourlyForecast.Companion.WIND

data class HourlyForecast(
        val timestamp: Long,
        /** The temperature, in Kelvin **/
        val temperature: Double,
        /** The min temperature, in Kelvin **/
        val minTemp: Double = -1.0,
        /** The max temperature, in Kelvin **/
        val maxTemp: Double = -1.0,
        /** The temperature, in hPa **/
        val pressure: Double = -1.0,
        /** The temperature, in percent **/
        val humidity: Double = -1.0,
        /** The icon, one of [NONE], [CLEAR], [CLOUDY], [COLD], [DRIZZLE], [HAZE], [FOG],
         *  [HAIL], [HEAVY_THUNDERSTORM], [HEAVY_THUNDERSTORM_WITH_RAIN], [HOT], [MOSTLY_CLOUDY],
         *  [PARTLY_CLOUDY], [SHOWERS], [SLEET], [SNOW], [STORM], [THUNDERSTORM],
         *  [THUNDERSTORM_WITH_RAIN], [WIND], [BROKEN_CLOUDS]**/
        val icon: Int,
        /** A text describing the current weather condition **/
        val condition: String,
        /** The clouds, percentage **/
        val clouds: Int = -1,
        /** Wind speed, in m/s **/
        val windSpeed: Double = -1.0,
        /** wind direction, in degrees **/
        val windDirection: Double = -1.0,
        /** rain, in mm per hour **/
        val precipitation: Double = -1.0,
        /** whether this forecast is during night time (whether a moon icon should be used instead of sun) **/
        val night: Boolean = false,
        /** Location string **/
        val location: String,
        /** Provider name **/
        val provider: String,
        /** Url to the provider and more weather information **/
        val providerUrl: String = "",
        /** Rain probability, in percent [0..100]. -1 if not available **/
        val precipProbability: Int = -1,
        /** Timestamp (in millis) when when this forecast was created **/
        val updateTime: Long
) {
    fun toDatabaseEntity(): ForecastEntity {
        return ForecastEntity(
                timestamp = timestamp,
                clouds = clouds,
                condition = condition,
                humidity = humidity,
                icon = icon,
                location = location,
                maxTemp = maxTemp,
                minTemp = minTemp,
                night = night,
                pressure = pressure,
                provider = provider,
                providerUrl = providerUrl,
                precipitation = precipitation,
                precipProbability = precipProbability,
                temperature = temperature,
                updateTime = updateTime,
                windDirection = windDirection,
                windSpeed = windSpeed,
                snow = -1.0,
                snowProbability = -1
        )
    }

    constructor(entity: ForecastEntity) : this(
            timestamp = entity.timestamp,
            clouds = entity.clouds,
            condition = entity.condition,
            humidity = entity.humidity,
            icon = entity.icon,
            location = entity.location,
            maxTemp = entity.maxTemp,
            minTemp = entity.minTemp,
            night = entity.night,
            pressure = entity.pressure,
            provider = entity.provider,
            providerUrl = entity.providerUrl,
            precipitation = entity.precipitation,
            precipProbability = entity.precipProbability,
            temperature = entity.temperature,
            updateTime = entity.updateTime,
            windDirection = entity.windDirection,
            windSpeed = entity.windSpeed
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