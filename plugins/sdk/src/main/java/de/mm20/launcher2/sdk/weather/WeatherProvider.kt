package de.mm20.launcher2.sdk.weather

import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.config.WeatherPluginConfig
import de.mm20.launcher2.plugin.contracts.WeatherPluginContract
import de.mm20.launcher2.sdk.base.BasePluginProvider
import de.mm20.launcher2.sdk.ktx.formatToString
import de.mm20.launcher2.sdk.utils.launchWithCancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

abstract class WeatherProvider(
    val config: WeatherPluginConfig,
) : BasePluginProvider() {
    override fun getPluginType(): PluginType {
        return PluginType.Weather
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return query(uri, projection, null, null)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?
    ): Cursor? {

        val context = context ?: return null
        checkPermissionOrThrow(context)

        when {
            uri.pathSegments.size == 1 && uri.pathSegments.first() == WeatherPluginContract.Paths.Weather -> {
                val lat = uri.getQueryParameter(WeatherPluginContract.ForecastParams.Lat)
                    ?.toDoubleOrNull()
                val lon = uri.getQueryParameter(WeatherPluginContract.ForecastParams.Lon)
                    ?.toDoubleOrNull()
                val id = uri.getQueryParameter(WeatherPluginContract.ForecastParams.Id)
                val name = uri.getQueryParameter(WeatherPluginContract.ForecastParams.LocationName)

                val forecasts = launchWithCancellationSignal(cancellationSignal) {
                    getWeatherData(lat, lon, id, name)
                } ?: return null
                return createForecastCursor(forecasts)
            }

            uri.pathSegments.size == 1 && uri.pathSegments.first() == WeatherPluginContract.Paths.Locations -> {
                val query =
                    uri.getQueryParameter(WeatherPluginContract.LocationParams.Query) ?: return null
                val locations = launchWithCancellationSignal(
                    cancellationSignal
                ) {
                    findLocations(query)
                }
                return createLocationsCursor(locations)
            }
        }
        return super.query(uri, projection, queryArgs, cancellationSignal)
    }

    private suspend fun getWeatherData(
        lat: Double?,
        lon: Double?,
        id: String?,
        locationName: String?
    ): List<Forecast>? {
        if (lat != null && lon != null && locationName == null) {
            return getWeatherData(lat, lon)
        }
        if (id != null && locationName != null) {
            return getWeatherData(WeatherLocation.Id(id, locationName))
        }
        if (locationName != null && lat != null && lon != null) {
            return getWeatherData(WeatherLocation.LatLon(locationName, lat, lon))
        }
        return null
    }

    private fun createForecastCursor(forecasts: List<Forecast>): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                WeatherPluginContract.ForecastColumns.Timestamp,
                WeatherPluginContract.ForecastColumns.CreatedAt,
                WeatherPluginContract.ForecastColumns.Temperature,
                WeatherPluginContract.ForecastColumns.TemperatureMin,
                WeatherPluginContract.ForecastColumns.TemperatureMax,
                WeatherPluginContract.ForecastColumns.Pressure,
                WeatherPluginContract.ForecastColumns.Humidity,
                WeatherPluginContract.ForecastColumns.WindSpeed,
                WeatherPluginContract.ForecastColumns.WindDirection,
                WeatherPluginContract.ForecastColumns.Precipitation,
                WeatherPluginContract.ForecastColumns.RainProbability,
                WeatherPluginContract.ForecastColumns.Clouds,
                WeatherPluginContract.ForecastColumns.Location,
                WeatherPluginContract.ForecastColumns.Provider,
                WeatherPluginContract.ForecastColumns.ProviderUrl,
                WeatherPluginContract.ForecastColumns.Night,
                WeatherPluginContract.ForecastColumns.Icon,
                WeatherPluginContract.ForecastColumns.Condition,
            ),
            forecasts.size,
        )
        for (forecast in forecasts) {
            cursor.addRow(
                arrayOf(
                    forecast.timestamp,
                    forecast.createdAt,
                    forecast.temperature.kelvin,
                    forecast.minTemp?.kelvin,
                    forecast.maxTemp?.kelvin,
                    forecast.pressure?.hPa,
                    forecast.humidity,
                    forecast.windSpeed?.metersPerSecond,
                    forecast.windDirection,
                    forecast.precipitation?.mm,
                    forecast.rainProbability,
                    forecast.clouds,
                    forecast.location,
                    forecast.provider,
                    forecast.providerUrl,
                    forecast.night,
                    forecast.icon.name,
                    forecast.condition,
                )
            )
        }
        return cursor
    }

    fun createLocationsCursor(locations: List<WeatherLocation>): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                WeatherPluginContract.LocationColumns.Id,
                WeatherPluginContract.LocationColumns.Lat,
                WeatherPluginContract.LocationColumns.Lon,
                WeatherPluginContract.LocationColumns.Name,
            ),
            locations.size,
        )
        for (location in locations) {
            if (location is WeatherLocation.Id) {
                cursor.addRow(
                    arrayOf(
                        location.id,
                        null,
                        null,
                        location.name,
                    )
                )
            } else if (location is WeatherLocation.LatLon) {
                cursor.addRow(
                    arrayOf(
                        null,
                        location.lat,
                        location.lon,
                        location.name,
                    )
                )
            }
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("This operation is not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("This operation is not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("This operation is not supported")
    }

    override fun getType(uri: Uri): String? {
        throw UnsupportedOperationException("This operation is not supported")
    }

    /**
     * Find locations based on a query string.
     * The default implementation uses the Android Geocoder and returns a list of lat lon locations.
     * It also supports lat/lon coordinates in the format "[lat] [lon] [name]" or "[lat] [lon]".
     */
    open suspend fun findLocations(query: String): List<WeatherLocation> {
        val context = context ?: return emptyList()
        val parts = query.split(" ", limit = 3)
        val lat = parts.getOrNull(0)?.toDoubleOrNull()
        val lon = parts.getOrNull(1)?.toDoubleOrNull()
        if (lat != null && lon != null && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) {
            val name = parts.getOrElse(2) { getLocationName(lat, lon) }
            return listOf(
                WeatherLocation.LatLon(name, lat, lon)
            )
        }
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context)
        val locations =
            withContext(Dispatchers.IO) {
                try {
                    geocoder.getFromLocationName(query, 10)
                } catch (e: IOException) {
                    Log.e("WeatherProvider", "Failed to lookup location", e)
                    emptyList()
                }
            } ?: emptyList()
        return locations.mapNotNull {
            WeatherLocation.LatLon(
                lat = it.latitude,
                lon = it.longitude,
                name = it.formatToString()
            )
        }
    }

    /**
     * Get the name of a location based on lat/lon coordinates.
     * The default implementation uses the Android Geocoder.
     * If the Geocoder is not available, the lat/lon coordinates are formatted as a string.
     */
    open suspend fun getLocationName(lat: Double, lon: Double): String {
        val context = context ?: return formatLatLon(lat, lon)
        if (!Geocoder.isPresent()) return formatLatLon(lat, lon)
        return withContext(Dispatchers.IO) {
            try {
                Geocoder(context).getFromLocation(lat, lon, 1)
                    ?.firstOrNull()
                    ?.formatToString() ?: formatLatLon(lat, lon)
            } catch (e: IOException) {
                formatLatLon(lat, lon)
            }
        }
    }

    private fun formatLatLon(lat: Double, lon: Double): String {
        val absLat = lat.absoluteValue
        val absLon = lon.absoluteValue

        val dLat = absLat.toInt()
        val dLon = absLon.toInt()

        val mLat = ((absLat - dLat) * 60).roundToInt()

        val mLon = ((absLon - dLon) * 60).roundToInt()


        val dmsLat = "$dLat°$mLat'${if (lat >= 0) "N" else "S"}"

        val dmsLon = "$dLon°$mLon'${if (lat >= 0) "E" else "W"}"

        return "$dmsLat $dmsLon"
    }

    /**
     * Get weather data for the current location. This is called when the user has set
     * the location to "Current location".
     */
    abstract suspend fun getWeatherData(lat: Double, lon: Double): List<Forecast>?

    /**
     * Get weather data for a set location. This is called when the user has set
     * the location to a custom location.
     * @param location the location that the user has set. This is guaranteed
     * to be one of the locations returned by [findLocations].
     * @return the weather data for the given location
     * Note that returned forecasts should use the same location name as the location parameter.
     */
    abstract suspend fun getWeatherData(location: WeatherLocation): List<Forecast>?
}