package de.mm20.launcher2.weather.metno

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.weather.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import org.shredzone.commons.suncalc.SunTimes
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MetNoProvider(override val context: Context) : LatLonWeatherProvider() {

    override val preferences: SharedPreferences
        get() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun isNight(timestamp: Long, lat: Double, lon: Double): Boolean {
        val sunTimes = SunTimes.compute().on(Date(timestamp)).at(lat, lon).execute()
        if (sunTimes.isAlwaysDown) return true
        if (sunTimes.isAlwaysUp) return false

        val set = sunTimes.set
        val rise = sunTimes.rise

        if (set == null && rise != null) {
            return timestamp < rise.toEpochSecond() * 1000
        }

        if (set != null && rise == null) {
            return set.toEpochSecond() * 1000 < timestamp
        }

        if (set == null || rise == null) return false

        if (set.toEpochSecond() < rise.toEpochSecond()) {
            return (set.toEpochSecond() * 1000 < timestamp && timestamp < rise.toEpochSecond() * 1000)
        }

        return !(rise.toEpochSecond() * 1000 < timestamp && timestamp < set.toEpochSecond() * 1000)

    }

    private fun conditionForCode(code: String): String {
        return context.getString(
            when (code.substringBefore("_")) {
                "sleetshowers" -> R.string.weather_condition_sleetshowers
                "heavysleet" -> R.string.weather_condition_heavysleet
                "lightrainshowersandthunder" -> R.string.weather_condition_lightrainshowersandthunder
                "heavyrain" -> R.string.weather_condition_heavyrain
                "lightsnowandthunder" -> R.string.weather_condition_lightsnowandthunder
                "lightrain" -> R.string.weather_condition_lightrain
                "lightrainshowers" -> R.string.weather_condition_lightrainshowers
                "lightsnow" -> R.string.weather_condition_lightsnow
                "heavysleetshowersandthunder" -> R.string.weather_condition_heavysleetshowersandthunder
                "lightsnowshowers" -> R.string.weather_condition_lightsnowshowers
                "lightssleetshowersandthunder" -> R.string.weather_condition_lightssleetshowersandthunder
                "snowandthunder" -> R.string.weather_condition_snowandthunder
                "heavysleetshowers" -> R.string.weather_condition_heavysleetshowers
                "heavysnow" -> R.string.weather_condition_heavysnow
                "cloudy" -> R.string.weather_condition_cloudy
                "lightrainandthunder" -> R.string.weather_condition_lightrainandthunder
                "snow" -> R.string.weather_condition_snow
                "heavysnowshowers" -> R.string.weather_condition_heavysnowshowers
                "heavyrainshowers" -> R.string.weather_condition_heavyrainshowers
                "rainshowersandthunder" -> R.string.weather_condition_rainshowersandthunder
                "clearsky" -> R.string.weather_condition_clearsky
                "sleet" -> R.string.weather_condition_sleet
                "rain" -> R.string.weather_condition_rain
                "sleetandthunder" -> R.string.weather_condition_sleetandthunder
                "lightssnowshowersandthunder" -> R.string.weather_condition_lightssnowshowersandthunder
                "heavyrainshowersandthunder" -> R.string.weather_condition_heavyrainshowersandthunder
                "fair" -> R.string.weather_condition_fair
                "fog" -> R.string.weather_condition_fog
                "sleetshowersandthunder" -> R.string.weather_condition_sleetshowersandthunder
                "rainandthunder" -> R.string.weather_condition_rainandthunder
                "lightsleet" -> R.string.weather_condition_lightsleet
                "heavysleetandthunder" -> R.string.weather_condition_heavysleetandthunder
                "partlycloudy" -> R.string.weather_condition_partlycloudy
                "heavysnowandthunder" -> R.string.weather_condition_heavysnowandthunder
                "rainshowers" -> R.string.weather_condition_rainshowers
                "lightsleetandthunder" -> R.string.weather_condition_lightsleetandthunder
                "heavysnowshowersandthunder" -> R.string.weather_condition_heavysnowshowersandthunder
                "lightsleetshowers" -> R.string.weather_condition_lightsleetshowers
                "snowshowersandthunder" -> R.string.weather_condition_snowshowersandthunder
                "snowshowers" -> R.string.weather_condition_snowshowers
                "heavyrainandthunder" -> R.string.weather_condition_heavyrainandthunder
                else -> R.string.weather_condition_unknown
            }
        )
    }

    private fun iconForCode(code: String): Int {
        return when (code.substringBefore("_")) {
            "clearsky" -> Forecast.CLEAR
            "fair" -> Forecast.PARTLY_CLOUDY
            "partlycloudy" -> Forecast.MOSTLY_CLOUDY
            "cloudy" -> Forecast.CLOUDY
            "rainshowers", "rain", "lightrainshowers", "lightrain" -> Forecast.DRIZZLE
            "rainshowersandthunder", "snowandthunder", "snowshowersandthunder",
            "lightssnowshowersandthunder", "lightsleetandthunder",
            "lightsnowandthunder" -> Forecast.THUNDERSTORM
            "sleetshowers", "sleet", "lightsleetshowers", "heavysleetshowers", "lightsleet",
            "heavysleet" -> Forecast.SLEET
            "snowshowers", "snow", "lightsnowshowers", "heavysnowshowers", "lightsnow",
            "heavysnow" -> Forecast.SNOW
            "heavyrain", "heavyrainshowers" -> Forecast.SHOWERS
            "heavyrainandthunder", "sleetshowersandthunder", "rainandthunder", "sleetandthunder",
            "lightrainshowersandthunder", "heavyrainshowersandthunder",
            "lightssleetshowersandthunder", "lightrainandthunder" -> Forecast.THUNDERSTORM_WITH_RAIN
            "fog" -> Forecast.FOG
            "heavysleetshowersandthunder",
            "heavysleetandthunder" -> Forecast.HEAVY_THUNDERSTORM_WITH_RAIN
            "heavysnowshowersandthunder", "heavysnowandthunder" -> Forecast.HEAVY_THUNDERSTORM
            else -> Forecast.NONE
        }
    }

    override fun isUpdateRequired(): Boolean {
        return getLastUpdate() + (1000 * 60 * 60) <= System.currentTimeMillis()
    }

    private fun getUserAgent(): String? {
        val contactData = getContactInfo() ?: return null

        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pi = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            pi.signingInfo.apkContentsSigners.firstOrNull()
        } else {
            val pi = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            pi.signatures.firstOrNull()
        }
        val signatureHash = if (signature != null) {
            val digest = MessageDigest.getInstance("SHA")
            digest.update(signature.toByteArray())
            Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
        } else "null"
        return "${context.packageName}/signature:$signatureHash $contactData"
    }

    override fun isAvailable(): Boolean {
        return getContactResId() != 0
    }

    private fun getContactInfo(): String? {
        val resId = getContactResId().takeIf { it != 0 } ?: return null
        return context.getString(resId).takeIf { it.isNotBlank() }
    }


    override val name: String
        get() = context.getString(R.string.provider_metno)

    private fun getContactResId(): Int {
        return context.resources.getIdentifier("metno_contact", "string", context.packageName)
    }

    override suspend fun loadWeatherData(location: LatLonWeatherLocation): WeatherUpdateResult<LatLonWeatherLocation>? {

        val lastUpdate = getLastUpdate()
        val httpDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ROOT)
        val ifModifiedSince = httpDateFormat.format(Date(lastUpdate))
        try {
            val forecasts = mutableListOf<Forecast>()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)

            val httpClient = OkHttpClient()

            val latParam = String.format(Locale.ROOT, "%.4f", location.lat)
            val lonParam = String.format(Locale.ROOT, "%.4f", location.lon)

            val forecastRequest = Request.Builder()
                .url("https://api.met.no/weatherapi/locationforecast/2.0/?lat=$latParam&lon=$lonParam")
                .addHeader("User-Agent", getUserAgent() ?: return null)
                .addHeader("If-Modified-Since", ifModifiedSince)
                .get()
                .build()

            val response = httpClient.newCall(forecastRequest).execute()
            val responseBody = response.body?.string() ?: return null

            val json = JSONObject(responseBody)
            val properties = json.getJSONObject("properties")
            val meta = properties.getJSONObject("meta")
            val updatedAt = dateFormat.parse(meta.getString("updated_at"))?.time
                ?: System.currentTimeMillis()
            val timeseries = properties.getJSONArray("timeseries")

            for (i in 0 until timeseries.length()) {
                val fc = timeseries.getJSONObject(i)
                val data = fc.getJSONObject("data")
                val timestamp = dateFormat.parse(fc.getString("time"))?.time ?: continue
                val details = data.getJSONObject("instant").getJSONObject("details")
                var hours = 0
                val nextHours = data.optJSONObject("next_1_hours")?.also { hours = 1 }
                    ?: data.optJSONObject("next_6_hours")?.also { hours = 6 }
                    ?: data.optJSONObject("next_12_hours")?.also { hours = 12 }
                    ?: continue
                val symbolCode = nextHours.optJSONObject("summary")?.getString("symbol_code")
                    ?: continue
                val precipitationAmount =
                    (nextHours.optJSONObject("details")?.optDouble("precipitation_amount")
                        ?: 0.0) / hours
                forecasts.add(
                    Forecast(
                        timestamp = timestamp,
                        temperature = details.getDouble("air_temperature") + 273.15,
                        updateTime = updatedAt,
                        clouds = details.getDouble("cloud_area_fraction").roundToInt(),
                        humidity = details.getDouble("relative_humidity"),
                        windDirection = details.getDouble("wind_from_direction"),
                        windSpeed = details.getDouble("wind_speed"),
                        pressure = details.getDouble("air_pressure_at_sea_level"),
                        location = location.name,
                        provider = context.getString(R.string.provider_metno),
                        providerUrl = "https://www.yr.no/",
                        icon = iconForCode(symbolCode),
                        condition = conditionForCode(symbolCode),
                        precipitation = precipitationAmount,
                        night = isNight(timestamp, location.lat, location.lon)

                    )
                )
            }
            return WeatherUpdateResult(
                forecasts = forecasts,
                location = location
            )
        } catch (e: JSONException) {
            CrashReporter.logException(e)
        } catch (e: IOException) {
            CrashReporter.logException(e)
        }
        return null
    }

    companion object {
        private const val PREFERENCES = "metno"
    }
}