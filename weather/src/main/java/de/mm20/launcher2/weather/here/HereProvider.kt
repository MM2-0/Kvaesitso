package de.mm20.launcher2.weather.here

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.util.Log
import androidx.core.content.edit
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.getDouble
import de.mm20.launcher2.ktx.putDouble
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.R
import de.mm20.launcher2.weather.WeatherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class HereProvider(val context: Context) : WeatherProvider() {
    override val supportsAutoLocation = true
    override val supportsManualLocation = true
    override var autoLocation: Boolean
        get() {
            return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(AUTO_LOCATION, true)
        }
        set(value) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit {
                    putBoolean(AUTO_LOCATION, value)
                }
        }

    override suspend fun fetchNewWeatherData(): List<Forecast>? {
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

        val updateTime = System.currentTimeMillis()

        var query: String? = null

        if (autoLocation) {
            var lat: Double? = null
            var lon: Double? = null
            if (context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                lat = location?.latitude
                lon = location?.longitude
                if (lat != null && lon != null) {
                    prefs.edit {
                        putDouble(LAST_LAT, lat!!)
                        putDouble(LAST_LON, lon!!)
                    }
                }
            }
            if (lat == null || lon == null) {
                lat = prefs.getDouble(LAST_LAT)
                lon = prefs.getDouble(LAST_LON)
            }
            if (lat != null && lon != null) query = "latitude=$lat&longitude=$lon"
        }
        if (!autoLocation || query == null) {
            val name = prefs.getString(CITY_NAME, null) ?: return null
            query = "name=$name"
        }

        val lang = Locale.getDefault().language
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT)

        val forecastList = mutableListOf<Forecast>()

        try {
            val httpClient = OkHttpClient()

            val forecastRequest = Request.Builder()
                .url("https://weather.ls.hereapi.com/weather/1.0/report.json?apiKey=${getApiKey()}&product=forecast_hourly&$query&language=$lang")
                .get()
                .build()

            val body = withContext(Dispatchers.IO) {
                httpClient.newCall(forecastRequest).execute().body?.string()
            } ?: run {
                Log.e("MM20", "Here provider: forecast request returned null")
                return null
            }

            val forecastLocation = JSONObject(body)
                .getJSONObject("hourlyForecasts")
                .getJSONObject("forecastLocation")
            val forecasts = forecastLocation.getJSONArray("forecast")

            val location = forecastLocation.getString("city")
            val locationLong =
                "${forecastLocation.getString("city")}, ${forecastLocation.getString("country")}"

            if (autoLocation) {
                prefs.edit {
                    putString(LAST_LOCATION, locationLong)
                }
            }

            for (i in 0 until forecasts.length()) {
                val forecast = forecasts.getJSONObject(i)

                val timestamp = try {
                    dateFormat.parse(forecast.getString("utcTime"))?.time ?: continue
                } catch (e: ParseException) {
                    CrashReporter.logException(e)
                    return null
                }

                // We don't want old weather data
                if (timestamp + 1000 * 60 * 30 < System.currentTimeMillis()) continue

                val condition = when {
                    !forecast.optString("precipitationDesc")
                        .isNullOrEmpty() -> forecast.optString("precipitationDesc")
                    !forecast.optString("skyDescription")
                        .isNullOrEmpty() -> forecast.optString("skyDescription")
                    !forecast.optString("temperatureDesc")
                        .isNullOrEmpty() -> forecast.optString("temperatureDesc")
                    else -> forecast.optString("description")
                }
                val humidity = forecast.getString("humidity").toIntOrNull() ?: 0
                val icon = getIcon(forecast.getString("iconName"))
                val night = forecast.getString("daylight") == "N"
                val rain = forecast.getString("rainFall").toDoubleOrNull() ?: 0.0
                val snow = forecast.getString("snowFall").toDoubleOrNull() ?: 0.0
                val rainPercent = forecast.getString("precipitationProbability").toIntOrNull() ?: 0
                val temperature = forecast.getString("temperature").toDoubleOrNull()?.plus(273.15)
                    ?: 0.0
                val windDir = forecast.getString("windDirection").toIntOrNull() ?: 0
                val windSpeed = forecast.getString("windSpeed").toDoubleOrNull() ?: 0.0

                forecastList.add(
                    Forecast(
                        timestamp = timestamp,
                        clouds = -1,
                        condition = condition,
                        humidity = humidity.toDouble(),
                        icon = icon,
                        location = location,
                        night = night,
                        pressure = -1.0,
                        provider = context.getString(R.string.provider_here),
                        providerUrl = "",
                        precipitation = rain * 10,
                        precipProbability = rainPercent,
                        temperature = temperature,
                        windDirection = windDir.toDouble(),
                        windSpeed = windSpeed,
                        updateTime = updateTime
                    )
                )
            }


        } catch (e: JSONException) {
            CrashReporter.logException(e)
            return null
        }

        prefs.edit {
            putLong(LAST_UPDATE, updateTime)
        }

        return forecastList
    }

    private fun getIcon(iconName: String): Int {
        with(Forecast) {
            return when (iconName) {
                "sunny" -> CLEAR
                "clear" -> CLEAR
                "mostly_sunny" -> PARTLY_CLOUDY
                "mostly_clear" -> PARTLY_CLOUDY
                "passing_clounds" -> MOSTLY_CLOUDY
                "more_sun_than_clouds" -> PARTLY_CLOUDY
                "scattered_clouds" -> PARTLY_CLOUDY
                "partly_cloudy" -> PARTLY_CLOUDY
                "a_mixture_of_sun_and_clouds" -> PARTLY_CLOUDY
                "increasing_cloudiness" -> MOSTLY_CLOUDY
                "breaks_of_sun_late" -> MOSTLY_CLOUDY
                "afternoon_clouds" -> MOSTLY_CLOUDY
                "morning_clouds" -> MOSTLY_CLOUDY
                "partly_sunny" -> MOSTLY_CLOUDY
                "high_level_clouds" -> PARTLY_CLOUDY
                "decreasing_cloudiness" -> PARTLY_CLOUDY
                "clearing_skies" -> PARTLY_CLOUDY
                "high_clouds" -> PARTLY_CLOUDY
                "rain_early" -> SHOWERS
                "heavy_rain_early" -> SHOWERS
                "strong_thunderstorms" -> HEAVY_THUNDERSTORM
                "severe_thunderstorms" -> HEAVY_THUNDERSTORM
                "thundershowers" -> THUNDERSTORM_WITH_RAIN
                "thunderstorms" -> THUNDERSTORM
                "tstorms_early" -> THUNDERSTORM_WITH_RAIN
                "isolated_tstorms_late" -> THUNDERSTORM
                "scattered_tstorms_late" -> THUNDERSTORM
                "tstorms_late" -> THUNDERSTORM_WITH_RAIN
                "tstorms" -> THUNDERSTORM_WITH_RAIN
                "ice_fog" -> FOG
                "more_clouds_than_sun" -> MOSTLY_CLOUDY
                "broken_clouds" -> MOSTLY_CLOUDY
                "scattered_showers" -> SHOWERS
                "a_few_showers" -> SHOWERS
                "light_showers" -> SHOWERS
                "passing_showers" -> SHOWERS
                "rain_showers" -> SHOWERS
                "showers" -> SHOWERS
                "widely_scattered_tstorms" -> THUNDERSTORM
                "isolated_tstorms" -> THUNDERSTORM
                "a_few_tstorms" -> THUNDERSTORM
                "scattered_tstorms" -> THUNDERSTORM
                "hazy_sunshine" -> HAZE
                "haze" -> HAZE
                "smoke" -> FOG
                "low_level_haze" -> HAZE
                "early_fog_followed_by_sunny_skies" -> HAZE
                "early_fog" -> FOG
                "light_fog" -> FOG
                "fog" -> FOG
                "dense_fog" -> FOG
                "night_haze" -> HAZE
                "night_smoke" -> FOG
                "night_low_level_haze" -> HAZE
                "night_widely_scattered_tstorms" -> THUNDERSTORM
                "night_isolated_tstorms" -> THUNDERSTORM
                "night_a_few_tstorms" -> THUNDERSTORM
                "night_scattered_tstorms" -> THUNDERSTORM
                "night_tstorms" -> THUNDERSTORM
                "night_clear" -> CLEAR
                "mostly_cloudy" -> MOSTLY_CLOUDY
                "cloudy" -> CLOUDY
                "overcast" -> CLOUDY
                "low_clouds" -> MOSTLY_CLOUDY
                "hail" -> HAIL
                "sleet" -> SLEET
                "light_mixture_of_precip" -> SLEET
                "icy_mix" -> SLEET
                "mixture_of_precip" -> SLEET
                "heavy_mixture_of_precip" -> SLEET
                "snow_changing_to_rain" -> SLEET
                "snow_changing_to_an_icy_mix" -> SLEET
                "an_icy_mix_changing_to_snow" -> SLEET
                "an_icy_mix_changing_to_rain" -> SLEET
                "rain_changing_to_snow" -> SLEET
                "rain_changing_to_an_icy_mix" -> SLEET
                "light_icy_mix_early" -> SLEET
                "icy_mix_early" -> SLEET
                "light_icy_mix_late" -> SLEET
                "icy_mix_late" -> SLEET
                "snow_rain_mix" -> SLEET
                "scattered_flurries" -> SNOW
                "snow_flurries" -> SNOW
                "light_snow_showers" -> SLEET
                "snow_showers" -> SLEET
                "light_snow" -> SNOW
                "flurries_early" -> SNOW
                "snow_showers_early" -> SLEET
                "light_snow_early" -> SNOW
                "flurries_late" -> SNOW
                "snow_showers_late" -> SLEET
                "light_snow_late" -> SNOW
                "night_decreasing_cloudiness" -> PARTLY_CLOUDY
                "night_clearing_skies" -> PARTLY_CLOUDY
                "night_high_level_clouds" -> PARTLY_CLOUDY
                "night_high_clouds" -> PARTLY_CLOUDY
                "night_scattered_showers" -> SHOWERS
                "night_a_few_showers" -> SHOWERS
                "night_light_showers" -> SHOWERS
                "night_passing_showers" -> SHOWERS
                "night_rain_showers" -> SHOWERS
                "night_sprinkles" -> DRIZZLE
                "night_showers" -> SHOWERS
                "night_mostly_clear" -> PARTLY_CLOUDY
                "night_passing_clouds" -> MOSTLY_CLOUDY
                "night_scattered_clouds" -> PARTLY_CLOUDY
                "night_partly_cloudy" -> PARTLY_CLOUDY
                "night_afternoon_clouds" -> MOSTLY_CLOUDY
                "night_morning_clouds" -> MOSTLY_CLOUDY
                "night_broken_clouds" -> MOSTLY_CLOUDY
                "night_mostly_cloudy" -> MOSTLY_CLOUDY
                "light_freezing_rain" -> HAIL
                "freezing_rain" -> HAIL
                "heavy_rain" -> SHOWERS
                "lots_of_rain" -> SHOWERS
                "tons_of_rain" -> SHOWERS
                "heavy_rain_late" -> SHOWERS
                "flash_floods" -> SHOWERS
                "flood" -> SHOWERS
                "drizzle" -> DRIZZLE
                "sprinkles" -> DRIZZLE
                "light_rain" -> DRIZZLE
                "sprinkles_early" -> DRIZZLE
                "light_rain_early" -> SHOWERS
                "sprinkles_late" -> DRIZZLE
                "light_rain_late" -> SHOWERS
                "rain" -> SHOWERS
                "numerous_showers" -> SHOWERS
                "showery" -> SHOWERS
                "showers_early" -> SHOWERS
                "showers_late" -> SHOWERS
                "rain_late" -> SHOWERS
                "snow" -> SNOW
                "moderate_snow" -> SNOW
                "snow_early" -> SNOW
                "snow_late" -> SNOW
                "heavy_snow" -> SNOW
                "heavy_snow_early" -> SNOW
                "heavy_snow_late" -> SNOW
                "tornado" -> STORM
                "tropical_storm" -> STORM
                "hurricane" -> STORM
                "sandstorm" -> STORM
                "duststorm" -> STORM
                "snowstorm" -> STORM
                "blizzard" -> STORM
                else -> NONE
            }
        }
    }


    override fun isUpdateRequired(): Boolean {
        return getLastUpdate() + (1000 * 60 * 60) <= System.currentTimeMillis()
    }

    override fun getLastUpdate(): Long {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getLong(LAST_UPDATE, 0)
    }

    override suspend fun lookupLocation(query: String): List<Pair<Any?, String>> {
        val urlString =
            "https://geocoder.ls.hereapi.com/6.2/geocode.json?apiKey=${getApiKey()}&searchtext=$query"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(urlString)
            .build()
        try {
            val body = withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                response.body?.string()
            } ?: return emptyList()
            val json = JSONObject(body)
            val results = json
                .optJSONObject("Response")
                ?.optJSONArray("View")
                ?.optJSONObject(0)
                ?.optJSONArray("Result") ?: return emptyList()
            val locations = mutableListOf<Pair<Any?, String>>()
            for (i in 0 until results.length()) {
                val result = results.getJSONObject(i)
                val location = result.optJSONObject("Location") ?: continue
                val name = location.optJSONObject("Address")?.getString("Label") ?: continue
                locations.add(URLEncoder.encode(name, "UTF-8") to name)
            }
            return locations
        } catch (e: JSONException) {
        } catch (e: IOException) {
        }
        return emptyList()
    }

    override fun setLocation(locationId: Any?, locationName: String) {
        val id = locationId as? String ?: return
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putString(CITY_NAME, id)
            putString(LAST_LOCATION, locationName)
        }
    }

    override fun getLastLocation(): String {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LAST_LOCATION, "")!!
    }

    override fun resetLastUpdate() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putLong(LAST_UPDATE, 0)
        }
    }

    private fun getApiKey(): String? {
        val resId = getApiKeyResId()
        if (resId != 0) return context.getString(resId)
        return null
    }

    override fun isAvailable(): Boolean {
        return getApiKeyResId() != 0
    }


    override val name: String
        get() = context.getString(R.string.provider_here)

    private fun getApiKeyResId(): Int {
        return context.resources.getIdentifier("here_key", "string", context.packageName)
    }

    companion object {
        private const val PREFERENCES = "here"
        private const val LAST_LAT = "last_lat"
        private const val LAST_LON = "last_lon"
        private const val LAST_UPDATE = "last_update"
        private const val CITY_NAME = "city_name"
        private const val LAST_LOCATION = "last_location"
        private const val AUTO_LOCATION = "auto_location"
    }
}