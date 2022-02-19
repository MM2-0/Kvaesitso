package de.mm20.launcher2.weather.brightsky

import android.content.Context
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.weather.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import java.lang.Exception
import kotlin.math.roundToInt

class BrightskyProvider(
    override val context: Context
) : LatLonWeatherProvider() {


    val apiClient by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.brightsky.dev/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create<BrightSkyApi>()
    }

    override suspend fun loadWeatherData(location: LatLonWeatherLocation): WeatherUpdateResult<LatLonWeatherLocation>? {

        val result = runCatching {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
            val date = Calendar.getInstance()
            date.timeInMillis -= 1000 * 60 * 30
            val startDate = format.format(date.timeInMillis)
            date.timeInMillis += 1000 * 60 * 60 * 24 * 14
            val endDate = format.format(date.timeInMillis)
            apiClient.weather(
                date = startDate,
                lastDate = endDate,
                lat = location.lat,
                lon = location.lon,
            )
        }.getOrElse {
            CrashReporter.logException(Exception(it))
            return null
        }
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
        val forecasts = mutableListOf<Forecast>()
        val updateTime = System.currentTimeMillis()
        for (weather in result.weather) {
            forecasts.add(
                Forecast(
                    timestamp = format.parse(weather.timestamp)?.time ?: continue,
                    clouds = weather.cloudCover?.roundToInt() ?: -1,
                    condition = getCondition(weather.icon ?: continue) ?: continue,
                    humidity = weather.relativeHumidity ?: -1.0,
                    icon = getIcon(weather.icon) ?: continue,
                    location = location.name,
                    maxTemp = weather.temperature ?: continue,
                    minTemp = weather.temperature,
                    night = (weather.sunshine ?: 100.0).roundToInt() == 0,
                    pressure = weather.pressureMsl ?: -1.0,
                    provider = "Deutscher Wetterdienst",
                    providerUrl = "https://www.dwd.de/",
                    precipitation = weather.precipitation ?: -1.0,
                    precipProbability = -1,
                    temperature = weather.temperature,
                    updateTime = updateTime,
                    windDirection = weather.windDirection ?: -1.0,
                    windSpeed = weather.windSpeed ?: -1.0
                )
            )
        }
        return WeatherUpdateResult(
            forecasts, location
        )
    }

    private fun getIcon(icon: String): Int? {
        return when (icon) {
            "clear-day", "clear-night" -> Forecast.CLEAR
            "partly-cloudy-day", "partly-cloudy-night" -> Forecast.PARTLY_CLOUDY
            "cloudy" -> Forecast.CLOUDY
            "fog" -> Forecast.FOG
            "wind" -> Forecast.WIND
            "rain" -> Forecast.SHOWERS
            "sleet" -> Forecast.SLEET
            "snow" -> Forecast.SNOW
            "hail" -> Forecast.HAIL
            "thunderstorm" -> Forecast.THUNDERSTORM
            else -> null
        }
    }

    private fun getCondition(icon: String): String? {
        val resId = when (icon) {
            "clear-day", "clear-night" -> R.string.weather_condition_clearsky
            "partly-cloudy-day", "partly-cloudy-night" -> R.string.weather_condition_partlycloudy
            "cloudy" -> R.string.weather_condition_cloudy
            "fog" -> R.string.weather_condition_fog
            "wind" -> R.string.weather_details_wind
            "rain" -> R.string.weather_condition_rain
            "sleet" -> R.string.weather_condition_sleet
            "snow" -> R.string.weather_condition_snow
            "hail" -> R.string.weather_condition_hail
            "thunderstorm" -> R.string.weather_condition_thunder
            else -> return null
        }
        return context.getString(resId)
    }

    override val preferences: SharedPreferences
        get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isUpdateRequired(): Boolean {
        return getLastUpdate() + 3600000 < System.currentTimeMillis()
    }

    override fun isAvailable(): Boolean {
        return true
    }

    override val name: String
        get() = context.getString(R.string.provider_brightsky)

    companion object {
        const val PREFS = "bright_sky"
    }
}