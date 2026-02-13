package de.mm20.launcher2.weather.breezy

import android.content.Context
import android.content.pm.PackageManager
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.preferences.weather.WeatherLocation
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.R
import de.mm20.launcher2.weather.WeatherIcon
import de.mm20.launcher2.weather.WeatherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import kotlin.time.Duration.Companion.days

class BreezyWeatherProvider(
    private val context: Context,
) : WeatherProvider, KoinComponent {
    private val database: AppDatabase by inject()

    override suspend fun getWeatherData(location: WeatherLocation): List<Forecast>? {
        // Noop implementation, because Breezy weather is handled in a special way
        return null
    }

    override suspend fun getWeatherData(
        lat: Double,
        lon: Double
    ): List<Forecast>? {
        // Noop implementation, because Breezy weather is handled in a special way
        return null
    }

    override suspend fun findLocation(query: String): List<WeatherLocation> {
        // Noop implementation, because Breezy weather is handled in a special way
        return emptyList()
    }

    override suspend fun getUpdateInterval(): Long {
        // Updates are pushed, no need to pull
        return 365.days.inWholeMilliseconds
    }

    internal suspend fun pushWeatherData(data: BreezyWeatherData) {
        val result = mutableListOf<Forecast>()

        val lastUpdate = data.timestamp?.times(1000L) ?: return

        val sunrises = buildList {
            if (data.sunRise != null) add(data.sunRise.times(1000L))
            if (data.forecasts != null) addAll(data.forecasts.mapNotNull { it.sunRise?.times(1000L) })
        }.sorted()

        val sunsets = buildList {
            if (data.sunSet != null) add(data.sunSet.times(1000L))
            if (data.forecasts != null) addAll(data.forecasts.mapNotNull { it.sunSet?.times(1000L) })
        }.sorted()


        for (hourly in data.hourly ?: emptyList()) {
            val timestamp = hourly.timestamp?.times(1000L) ?: continue

            val lastSunrise = sunrises.findLast { it < timestamp }
            val lastSunset = sunsets.findLast { it < timestamp }
            val nextSunrise = sunrises.find { it > timestamp }
            val nextSunset = sunsets.find { it > timestamp }

            val isNight = when {
                lastSunrise != null && lastSunset != null -> lastSunrise < lastSunset
                nextSunrise != null && nextSunset != null -> nextSunrise < nextSunset
                lastSunset != null && lastSunrise == null -> true
                nextSunrise != null && nextSunset == null -> true
                else -> false
            }

            result += Forecast(
                timestamp = timestamp,
                temperature = hourly.temp?.toDouble() ?: continue,
                icon = iconForId(hourly.conditionCode ?: continue).id,
                condition = textForId(hourly.conditionCode) ?: continue,
                location = data.location ?: return,
                provider = "Breezy Weather",
                humidity = hourly.humidity?.toDouble(),
                windSpeed = hourly.windSpeed?.toDouble()?.div(3.6),
                precipProbability = hourly.precipProbability,
                windDirection = hourly.windDirection?.toDouble(),
                updateTime = lastUpdate,
                night = isNight
            )
        }

        withContext(Dispatchers.IO) {
            val in7Days = System.currentTimeMillis() + Duration.ofDays(7).toMillis()
            database.weatherDao()
                .replaceAll(result.takeWhile { it.timestamp < in7Days  }.map { it.toDatabaseEntity() })
        }
    }

    private fun iconForId(id: Int): WeatherIcon {
        return when (id) {
            800 -> WeatherIcon.Clear
            801 -> WeatherIcon.PartlyCloudy
            803 -> WeatherIcon.Overcast
            500 -> WeatherIcon.Rain
            600 -> WeatherIcon.Snow
            771 -> WeatherIcon.Wind
            741 -> WeatherIcon.Fog
            751 -> WeatherIcon.Haze
            611 -> WeatherIcon.Sleet
            511 -> WeatherIcon.Hail
            210 -> WeatherIcon.Thunderstorm
            211 -> WeatherIcon.Thunderstorm
            else -> WeatherIcon.Unknown
        }
    }

    private fun textForId(id: Int): String? {
        val resId = when (id) {
            800 -> R.string.weather_condition_clearsky
            801 -> R.string.weather_condition_partlycloudy
            803 -> R.string.weather_condition_cloudy
            500 -> R.string.weather_condition_rain
            600 -> R.string.weather_condition_snow
            771 -> R.string.weather_condition_wind
            741 -> R.string.weather_condition_fog
            751 -> R.string.weather_condition_haze
            611 -> R.string.weather_condition_sleet
            511 -> R.string.weather_condition_hail
            210 -> R.string.weather_condition_thunder
            211 -> R.string.weather_condition_thunderstorm
            else -> R.string.weather_condition_unknown
        }
        return context.getString(resId)
    }

    companion object {
        internal fun isAvailable(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo("org.breezyweather", 0)
                return true
            } catch (_: PackageManager.NameNotFoundException) {
                return false
            }
        }

        const val Id = "breezy"
    }
}