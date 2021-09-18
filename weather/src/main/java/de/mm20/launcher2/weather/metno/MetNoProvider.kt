package de.mm20.launcher2.weather.metno

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.ktx.formatToString
import de.mm20.launcher2.ktx.getDouble
import de.mm20.launcher2.ktx.putDouble
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.R
import de.mm20.launcher2.weather.WeatherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.userAgent
import org.json.JSONException
import org.json.JSONObject
import org.shredzone.commons.suncalc.SunTimes
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MetNoProvider(val context: Context) : WeatherProvider() {
    override val supportsAutoLocation: Boolean
        get() = true
    override val supportsManualLocation: Boolean
        get() = Geocoder.isPresent()
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
        var lat: Double? = null
        var lon: Double? = null
        var locationName: String? = null
        val updateTime = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

        val lastUpdate = prefs.getLong(LAST_UPDATE, 0L)
        val httpDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ROOT)
        val ifModifiedSince = httpDateFormat.format(Date(lastUpdate))

        if (autoLocation &&
            context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            lat = location?.latitude
            lon = location?.longitude
            if (Geocoder.isPresent() && lat != null && lon != null) {
                try {
                    locationName = Geocoder(context).getFromLocation(lat, lon, 1)
                        .firstOrNull()
                        ?.formatToString() ?: "$lat/$lon"
                    prefs.edit {
                        putString(LAST_LOCATION_NAME, locationName)
                        lat?.let { putDouble(LAST_LAT, it) }
                        lon?.let { putDouble(LAST_LON, it) }
                    }
                } catch (e: IOException) {
                    CrashReporter.logException(e)
                    return null
                }
            }
        }
        if (!autoLocation) {
            if (!prefs.contains(LON) || !prefs.contains(LAT)) return null
            lat = prefs.getDouble(LAT)
            lon = prefs.getDouble(LON)

            locationName = prefs.getString(LAST_LOCATION_NAME, null) ?: "$lat/$lon"
        }
        if (lat == null || lon == null) {
            if (!prefs.contains(LAST_LON) || !prefs.contains(LAST_LAT)) return null
            lat = prefs.getDouble(LAST_LAT)
            lon = prefs.getDouble(LAST_LON)

            locationName = prefs.getString(LAST_LOCATION_NAME, null) ?: "$lat/$lon"
        }

        if (lat == null || lon == null || locationName == null) return null

        try {
            val forecasts = mutableListOf<Forecast>()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)

            val httpClient = OkHttpClient()

            val latParam = String.format(Locale.ROOT, "%.4f", lat)
            val lonParam = String.format(Locale.ROOT, "%.4f", lon)

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
                        location = locationName,
                        provider = context.getString(R.string.provider_metno),
                        providerUrl = "https://www.yr.no/",
                        icon = iconForCode(symbolCode),
                        condition = conditionForCode(symbolCode),
                        precipitation = precipitationAmount,
                        night = isNight(timestamp, lat, lon)

                    )
                )
            }

            prefs.edit {
                putLong(LAST_UPDATE, updateTime)
            }
            return forecasts
        } catch (e: JSONException) {
            CrashReporter.logException(e)
        } catch (e: IOException) {
            CrashReporter.logException(e)
        }
        return null
    }

    private fun isNight(timestamp: Long, lat: Double, lon: Double): Boolean {
        val sunTimes = SunTimes.compute().on(Date(timestamp)).at(lat, lon).execute()
        if (sunTimes.isAlwaysDown) return true
        if (sunTimes.isAlwaysUp) return false

        val set = sunTimes.set
        val rise = sunTimes.rise

        if (set == null && rise != null) {
            return timestamp < rise.time
        }

        if (set != null && rise == null) {
            return set.time < timestamp
        }

        if (set == null || rise == null) return false

        if (set.time < rise.time) {
            return (set.time < timestamp && timestamp < rise.time)
        }

        return !(rise.time < timestamp && timestamp < set.time)

    }

    private fun isSnow(code: String): Boolean {
        return code.contains("snow")
    }

    private fun conditionForCode(code: String): String {
        return context.getString(
            when (code.substringBefore("_")) {
                "sleetshowers" -> R.string.weather_sleetshowers
                "heavysleet" -> R.string.weather_heavysleet
                "lightrainshowersandthunder" -> R.string.weather_lightrainshowersandthunder
                "heavyrain" -> R.string.weather_heavyrain
                "lightsnowandthunder" -> R.string.weather_lightsnowandthunder
                "lightrain" -> R.string.weather_lightrain
                "lightrainshowers" -> R.string.weather_lightrainshowers
                "lightsnow" -> R.string.weather_lightsnow
                "heavysleetshowersandthunder" -> R.string.weather_heavysleetshowersandthunder
                "lightsnowshowers" -> R.string.weather_lightsnowshowers
                "lightssleetshowersandthunder" -> R.string.weather_lightssleetshowersandthunder
                "snowandthunder" -> R.string.weather_snowandthunder
                "heavysleetshowers" -> R.string.weather_heavysleetshowers
                "heavysnow" -> R.string.weather_heavysnow
                "cloudy" -> R.string.weather_cloudy
                "lightrainandthunder" -> R.string.weather_lightrainandthunder
                "snow" -> R.string.weather_snow
                "heavysnowshowers" -> R.string.weather_heavysnowshowers
                "heavyrainshowers" -> R.string.weather_heavyrainshowers
                "rainshowersandthunder" -> R.string.weather_rainshowersandthunder
                "clearsky" -> R.string.weather_clearsky
                "sleet" -> R.string.weather_sleet
                "rain" -> R.string.weather_rain
                "sleetandthunder" -> R.string.weather_sleetandthunder
                "lightssnowshowersandthunder" -> R.string.weather_lightssnowshowersandthunder
                "heavyrainshowersandthunder" -> R.string.weather_heavyrainshowersandthunder
                "fair" -> R.string.weather_fair
                "fog" -> R.string.weather_fog
                "sleetshowersandthunder" -> R.string.weather_sleetshowersandthunder
                "rainandthunder" -> R.string.weather_rainandthunder
                "lightsleet" -> R.string.weather_lightsleet
                "heavysleetandthunder" -> R.string.weather_heavysleetandthunder
                "partlycloudy" -> R.string.weather_partlycloudy
                "heavysnowandthunder" -> R.string.weather_heavysnowandthunder
                "rainshowers" -> R.string.weather_rainshowers
                "lightsleetandthunder" -> R.string.weather_lightsleetandthunder
                "heavysnowshowersandthunder" -> R.string.weather_heavysnowshowersandthunder
                "lightsleetshowers" -> R.string.weather_lightsleetshowers
                "snowshowersandthunder" -> R.string.weather_snowshowersandthunder
                "snowshowers" -> R.string.weather_snowshowers
                "heavyrainandthunder" -> R.string.weather_heavyrainandthunder
                else -> R.string.weather_unknown
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

    override fun getLastUpdate(): Long {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getLong(LAST_UPDATE, 0)
    }

    override suspend fun lookupLocation(query: String): List<Pair<Any?, String>> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context)
        val locations =
            withContext(Dispatchers.IO) {
                geocoder.getFromLocationName(query, 10)
            }
        return locations.mapNotNull {
            (it.latitude to it.longitude) to it.formatToString()
        }
    }

    /**
     * locationId must be a Pair<Double, Double> with the latitude as first and longitude as second
     * parameter
     */
    override fun setLocation(locationId: Any?, locationName: String) {
        if (locationId !is Pair<*, *>) return
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putDouble(LAT, locationId.first as Double)
            putDouble(LON, locationId.second as Double)
            putString(LAST_LOCATION_NAME, locationName)
        }
    }

    override fun getLastLocation(): String {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LAST_LOCATION_NAME, "")!!
    }

    override fun resetLastUpdate() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                putLong(LAST_UPDATE, 0L)
            }
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
        return "${context.packageName}[signature:$signatureHash] $contactData"
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

    companion object {
        private const val PREFERENCES = "metno"
        private const val AUTO_LOCATION = "auto_location"
        private const val LAST_UPDATE = "last_update"
        private const val EXPIRES = "expires"
        private const val LAT = "lat"
        private const val LON = "lon"
        private const val LAST_LAT = "last_lat"
        private const val LAST_LON = "last_lon"
        private const val LAST_LOCATION_NAME = "last_location_name"
    }
}