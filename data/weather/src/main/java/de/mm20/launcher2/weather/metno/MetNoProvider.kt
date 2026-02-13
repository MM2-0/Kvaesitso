package de.mm20.launcher2.weather.metno

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.WorkerThread
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.weather.WeatherLocation
import de.mm20.launcher2.preferences.weather.WeatherSettings
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.GeocoderWeatherProvider
import de.mm20.launcher2.weather.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.shredzone.commons.suncalc.SunTimes
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

internal class MetNoProvider(
    private val context: Context,
    private val weatherSettings: WeatherSettings,
) : GeocoderWeatherProvider(context) {

    private val metNoApi = MetNoApi()

    override suspend fun getWeatherData(location: WeatherLocation): List<Forecast>? {
        return when (location) {
            is WeatherLocation.LatLon -> withContext(Dispatchers.IO) {
                getWeatherData(location.lat, location.lon, location.name)
            }

            else -> {
                Log.e("MetNoProvider", "Unsupported location type: $location")
                null
            }
        }
    }

    override suspend fun getWeatherData(lat: Double, lon: Double): List<Forecast>? {
        val locationName = getLocationName(lat, lon)
        return withContext(Dispatchers.IO) {
            getWeatherData(lat, lon, locationName)
        }
    }

    @WorkerThread
    private suspend fun getWeatherData(
        lat: Double,
        lon: Double,
        locationName: String
    ): List<Forecast>? {
        val lastUpdate = weatherSettings.lastUpdate.first()
        try {
            val forecasts = mutableListOf<Forecast>()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            dateFormat.timeZone = TimeZone.getTimeZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC))

            val data = try {
                metNoApi.locationForecast(lat, lon, getUserAgent() ?: return null, lastUpdate)
            } catch (e: Exception) {
                CrashReporter.logException(e)
                return null
            }

            val properties = data.properties
            val meta = properties.meta
            val updatedAt = dateFormat.parse(meta.updatedAt)?.time
                ?: System.currentTimeMillis()
            val timeseries = properties.timeseries

            for (i in 0 until timeseries.size) {
                val fc = timeseries[i]
                val data = fc.data
                val timestamp = dateFormat.parse(fc.time)?.time ?: continue
                val details = data.instant.details
                var hours = 0
                val nextHours = data.next1Hours?.also { hours = 1 }
                    ?: data.next6Hours?.also { hours = 6 }
                    ?: data.next12Hours?.also { hours = 12 }
                    ?: continue
                val symbolCode = nextHours.summary?.symbolCode
                    ?: continue
                val precipitationAmount =
                    (nextHours.details?.precipitationAmount ?: 0.0) / hours
                forecasts.add(
                    Forecast(
                        timestamp = timestamp,
                        temperature = details.airTemperature + 273.15,
                        updateTime = updatedAt,
                        clouds = details.cloudAreaFraction.roundToInt(),
                        humidity = details.relativeHumidity,
                        windDirection = details.windFromDirection,
                        windSpeed = details.windSpeed,
                        pressure = details.airPressureAtSeaLevel,
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
            return forecasts
        } catch (e: SerializationException) {
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

    private fun getUserAgent(): String? {
        val contactData = getContactInfo() ?: return null

        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pi = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            pi.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            val pi = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            pi.signatures?.firstOrNull()
        }
        val signatureHash = if (signature != null) {
            val digest = MessageDigest.getInstance("SHA")
            digest.update(signature.toByteArray())
            Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
        } else "null"
        return "${context.packageName}/signature:$signatureHash $contactData"
    }

    private fun getContactInfo(): String? {
        val resId = getContactResId(context).takeIf { it != 0 } ?: return null
        return context.getString(resId).takeIf { it.isNotBlank() }
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
            "clearsky", "fair" -> Forecast.CLEAR
            "partlycloudy" -> Forecast.PARTLY_CLOUDY
            "cloudy" -> Forecast.OVERCAST
            "fog" -> Forecast.FOG

            "lightrain", "lightrainshowers" -> Forecast.LIGHT_RAIN
            "heavyrain", "heavyrainshowers" -> Forecast.HEAVY_RAIN
            "rain" -> Forecast.RAIN

            "lightsleet", "lightsleetshowers", "heavysleet", "heavysleetshowers", "sleet" -> Forecast.SLEET

            "snow", "lightsnow", "lightsnowshowers", "heavysnow", "heavysnowshowers" -> Forecast.SNOW

            "rainandthunder",
            "heavyrainandthunder",
            "heavyrainshowersandthunder",
            "lightrainshowersandthunder",
            "lightrainandthunder",
            "rainshowersandthunder",
            "lightssnowshowersandthunder",
            "lightsnowshowersandthunder",
            "snowshowersandthunder",
            "heavysnowandthunder",
            "heavysnowshowersandthunder",
            "heavysleetandthunder",
            "lightssleetshowersandthunder",
            "sleetshowersandthunder" -> Forecast.THUNDERSTORM

            else -> Forecast.UNKNOWN
        }
    }

    companion object {
        fun isAvailable(context: Context): Boolean {
            return getContactResId(context) != 0
        }

        private fun getContactResId(context: Context): Int {
            return context.resources.getIdentifier("metno_contact", "string", context.packageName)
        }

        internal const val Id = "metno"
    }
}