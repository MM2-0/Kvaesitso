package de.mm20.launcher2.weather.plugin

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.util.Log
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.plugin.config.WeatherPluginConfig
import de.mm20.launcher2.plugin.contracts.PluginContract
import de.mm20.launcher2.plugin.contracts.WeatherPluginContract
import de.mm20.launcher2.preferences.weather.WeatherLocation
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.WeatherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

internal class PluginWeatherProvider(
    private val context: Context,
    private val pluginAuthority: String,
) : WeatherProvider {
    override suspend fun getWeatherData(location: WeatherLocation): List<Forecast>? {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(pluginAuthority)
            .path(WeatherPluginContract.Paths.Forecasts).apply {
                if (location is WeatherLocation.LatLon) {
                    appendQueryParameter(
                        WeatherPluginContract.ForecastParams.Lat,
                        location.lat.toString()
                    )
                    appendQueryParameter(
                        WeatherPluginContract.ForecastParams.Lon,
                        location.lon.toString()
                    )
                } else if (location is WeatherLocation.Id) {
                    appendQueryParameter(
                        WeatherPluginContract.ForecastParams.Id,
                        location.locationId
                    )
                }
            }
            .appendQueryParameter(WeatherPluginContract.ForecastParams.LocationName, location.name)
            .appendQueryParameter(WeatherPluginContract.ForecastParams.Language, getLang())
            .build()

        return getWeatherData(uri)
    }

    override suspend fun getWeatherData(lat: Double, lon: Double): List<Forecast>? {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(pluginAuthority)
            .path(WeatherPluginContract.Paths.Forecasts)
            .appendQueryParameter(WeatherPluginContract.ForecastParams.Lat, lat.toString())
            .appendQueryParameter(WeatherPluginContract.ForecastParams.Lon, lon.toString())
            .appendQueryParameter(WeatherPluginContract.ForecastParams.Language, getLang())
            .build()

        return getWeatherData(uri)
    }

    private suspend fun getWeatherData(uri: Uri): List<Forecast>? = withContext(Dispatchers.IO) {
        val cancellationSignal = CancellationSignal()
        return@withContext suspendCancellableCoroutine {
            it.invokeOnCancellation {
                cancellationSignal.cancel()
            }
            val cursor = try {
                context.contentResolver.query(
                    uri,
                    null,
                    null,
                    cancellationSignal
                )
            } catch (e: Exception) {
                Log.e("MM20", "Plugin $pluginAuthority threw exception")
                CrashReporter.logException(e)
                it.resume(null)
                return@suspendCancellableCoroutine
            }

            if (cursor == null) {
                Log.e("MM20", "(getWeatherData) Plugin $pluginAuthority returned null cursor")
                it.resume(null)
                return@suspendCancellableCoroutine
            }

            val results = forecastsFromCursor(cursor) ?: emptyList()
            it.resume(results)
        }
    }

    private fun forecastsFromCursor(cursor: Cursor): List<Forecast>? {
        return cursor.use {
            val results = mutableListOf<Forecast>()

            val timestampIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.Timestamp)
                    .takeIf { it >= 0 } ?: return null
            val createdAtIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.CreatedAt)
                    .takeIf { it >= 0 } ?: return null
            val temperatureIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.Temperature)
                    .takeIf { it >= 0 } ?: return null
            val conditionIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.Condition)
                    .takeIf { it >= 0 } ?: return null
            val iconIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.Icon).takeIf { it >= 0 }
                    ?: return null

            val locationIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.Location)
                    .takeIf { it >= 0 }
                    ?: return null

            val providerIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.Provider)
                    .takeIf { it >= 0 }
                    ?: return null

            val providerUrlIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.ProviderUrl)
                    .takeIf { it >= 0 }

            val precipitationIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.Precipitation)
                    .takeIf { it >= 0 }

            val precipProbabilityIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.RainProbability)
                    .takeIf { it >= 0 }

            val cloudsIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.Clouds)
                    .takeIf { it >= 0 }

            val humidityIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.Humidity)
                    .takeIf { it >= 0 }

            val windSpeedIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.WindSpeed)
                    .takeIf { it >= 0 }

            val windDirectionIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.WindDirection)
                    .takeIf { it >= 0 }

            val pressureIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.Pressure)
                    .takeIf { it >= 0 }

            val nightIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.Night)
                    .takeIf { it >= 0 }

            val minTempIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.TemperatureMin)
                    .takeIf { it >= 0 }

            val maxTempIndex =
                cursor.getColumnIndex(WeatherPluginContract.ForecastColumns.TemperatureMax)
                    .takeIf { it >= 0 }



            while (cursor.moveToNext()) {
                results += Forecast(
                    timestamp = cursor.getLongOrNull(timestampIndex) ?: continue,
                    temperature = cursor.getDoubleOrNull(temperatureIndex) ?: continue,
                    updateTime = cursor.getLongOrNull(createdAtIndex) ?: continue,
                    condition = cursor.getStringOrNull(conditionIndex) ?: continue,
                    icon = getIcon(cursor.getStringOrNull(iconIndex) ?: continue),
                    location = cursor.getStringOrNull(locationIndex) ?: continue,
                    provider = cursor.getStringOrNull(providerIndex) ?: continue,
                    providerUrl = providerUrlIndex?.let { cursor.getStringOrNull(it) } ?: "",
                    clouds = cloudsIndex?.let { cursor.getIntOrNull(it) },
                    humidity = humidityIndex?.let { cursor.getDoubleOrNull(it) },
                    precipitation = precipitationIndex?.let { cursor.getDoubleOrNull(it) },
                    precipProbability = precipProbabilityIndex?.let { cursor.getIntOrNull(it) },
                    windSpeed = windSpeedIndex?.let { cursor.getDoubleOrNull(it) },
                    windDirection = windDirectionIndex?.let { cursor.getDoubleOrNull(it) },
                    pressure = pressureIndex?.let { cursor.getDoubleOrNull(it) },
                    night = nightIndex?.let { cursor.getIntOrNull(it) } == 1,
                    minTemp = minTempIndex?.let { cursor.getDoubleOrNull(it) },
                    maxTemp = maxTempIndex?.let { cursor.getDoubleOrNull(it) },
                )
            }
            results
        }
    }

    private fun getIcon(icon: String): Int {
        return when (icon) {
            "Clear" -> Forecast.CLEAR
            "Cloudy" -> Forecast.CLOUDY
            "Cold" -> Forecast.COLD
            "Drizzle" -> Forecast.DRIZZLE
            "Haze" -> Forecast.HAZE
            "Fog" -> Forecast.FOG
            "Hail" -> Forecast.HAIL
            "HeavyThunderstorm" -> Forecast.HEAVY_THUNDERSTORM
            "HeavyThunderstormWithRain" -> Forecast.HEAVY_THUNDERSTORM_WITH_RAIN
            "Hot" -> Forecast.HOT
            "MostlyCloudy" -> Forecast.MOSTLY_CLOUDY
            "PartlyCloudy" -> Forecast.PARTLY_CLOUDY
            "Showers" -> Forecast.SHOWERS
            "Sleet" -> Forecast.SLEET
            "Snow" -> Forecast.SNOW
            "Storm" -> Forecast.STORM
            "Thunderstorm" -> Forecast.THUNDERSTORM
            "ThunderstormWithRain" -> Forecast.THUNDERSTORM_WITH_RAIN
            "Wind" -> Forecast.WIND
            "BrokenClouds" -> Forecast.BROKEN_CLOUDS
            else -> Forecast.NONE
        }
    }

    private fun getLang(): String {
        return Locale.getDefault().language
    }

    override suspend fun findLocation(query: String): List<WeatherLocation> = withContext(Dispatchers.IO) {
        val cancellationSignal = CancellationSignal()
        return@withContext suspendCancellableCoroutine {
            it.invokeOnCancellation {
                cancellationSignal.cancel()
            }
            val uri = Uri.Builder()
                .scheme("content")
                .authority(pluginAuthority)
                .path(WeatherPluginContract.Paths.Locations)
                .appendQueryParameter(WeatherPluginContract.LocationParams.Query, query)
                .appendQueryParameter(WeatherPluginContract.LocationParams.Language, getLang())
                .build()

            val cursor = try {
                context.contentResolver.query(
                    uri,
                    null,
                    null,
                    cancellationSignal
                )
            } catch (e: Exception) {
                Log.e("MM20", "Plugin $pluginAuthority threw exception")
                CrashReporter.logException(e)
                it.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            if (cursor == null) {
                Log.e("MM20", "Plugin $pluginAuthority returned null cursor")
                it.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val results = locationsFromCursor(cursor) ?: emptyList()
            it.resume(results)
        }
    }

    private fun locationsFromCursor(cursor: Cursor): List<WeatherLocation> {
        return cursor.use {
            val results = mutableListOf<WeatherLocation>()

            val nameIndex =
                cursor.getColumnIndex(WeatherPluginContract.LocationColumns.Name)
                    .takeIf { it >= 0 } ?: return emptyList()
            val latIndex =
                cursor.getColumnIndex(WeatherPluginContract.LocationColumns.Lat)
                    .takeIf { it >= 0 }
            val lonIndex =
                cursor.getColumnIndex(WeatherPluginContract.LocationColumns.Lon)
                    .takeIf { it >= 0 }
            val locationIdIndex =
                cursor.getColumnIndex(WeatherPluginContract.LocationColumns.Id)
                    .takeIf { it >= 0 }

            while (cursor.moveToNext()) {
                val lat = latIndex?.let { cursor.getDoubleOrNull(it) }
                val lon = lonIndex?.let { cursor.getDoubleOrNull(it) }
                val locationId = locationIdIndex?.let { cursor.getStringOrNull(it) }
                val name = cursor.getStringOrNull(nameIndex) ?: continue

                if (lat != null && lon != null) {
                    results += WeatherLocation.LatLon(lat = lat, lon = lon, name = name)
                } else if (locationId != null) {
                    results += WeatherLocation.Id(locationId = locationId, name = name)
                }
            }
            results
        }
    }

    override suspend fun getUpdateInterval(): Long {
        return getPluginConfig()?.minUpdateInterval ?: super.getUpdateInterval()
    }

    private fun getPluginConfig(): WeatherPluginConfig? {
        val configBundle = try {
            context.contentResolver.call(
                Uri.Builder()
                    .scheme("content")
                    .authority(pluginAuthority)
                    .build(),
                PluginContract.Methods.GetConfig,
                null,
                null
            ) ?: return null
        } catch (e: Exception) {
            Log.e("PluginWeatherProvider", "Plugin $pluginAuthority threw exception", e)
            CrashReporter.logException(e)
            return null
        }

        return WeatherPluginConfig(configBundle)
    }
}