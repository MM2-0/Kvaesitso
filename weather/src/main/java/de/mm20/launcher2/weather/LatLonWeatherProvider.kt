package de.mm20.launcher2.weather

import android.location.Geocoder
import androidx.core.content.edit
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.formatToString
import de.mm20.launcher2.ktx.getDouble
import de.mm20.launcher2.ktx.putDouble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A WeatherProvider that uses lat/lon locations only (instead of provider specific location IDs)
 */
abstract class LatLonWeatherProvider : WeatherProvider<LatLonWeatherLocation>() {


    override suspend fun lookupLocation(query: String): List<LatLonWeatherLocation> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context)
        val locations =
            withContext(Dispatchers.IO) {
                geocoder.getFromLocationName(query, 10)
            }
        return locations.mapNotNull {
            LatLonWeatherLocation(
                lat = it.latitude,
                lon = it.longitude,
                name = it.formatToString()
            )
        }
    }

    override suspend fun loadWeatherData(
        lat: Double,
        lon: Double
    ): WeatherUpdateResult<LatLonWeatherLocation>? {
        return try {
            val locationName = Geocoder(context).getFromLocation(lat, lon, 1)
                .firstOrNull()
                ?.formatToString() ?: "$lat/$lon"
            loadWeatherData(
                LatLonWeatherLocation(
                    name = locationName,
                    lat = lat,
                    lon = lon
                )
            )
        } catch (e: IOException) {
            CrashReporter.logException(e)
            null
        }
    }

    override fun setLocation(location: WeatherLocation?) {
        location as LatLonWeatherLocation?
        preferences.edit {
            if (location == null) {
                remove(LAT)
                remove(LON)
                remove(LOCATION_NAME)
            } else {
                putDouble(LAT, location.lat)
                putDouble(LON, location.lon)
                putString(LOCATION_NAME, location.name)
            }
        }
    }

    override fun getLocation(): LatLonWeatherLocation? {
        val lat = preferences.getDouble(LAT) ?: return null
        val lon = preferences.getDouble(LON) ?: return null
        val name = preferences.getString(LOCATION_NAME, null) ?: return null
        return LatLonWeatherLocation(
            name = name,
            lat = lat,
            lon = lon
        )
    }

    override fun saveLastLocation(location: LatLonWeatherLocation) {
        preferences.edit {
            putDouble(LAST_LAT, location.lat)
            putDouble(LAST_LON, location.lon)
            putString(LAST_LOCATION_NAME, location.name)
        }
    }

    override fun getLastLocation(): LatLonWeatherLocation? {
        val lat = preferences.getDouble(LAST_LAT) ?: return null
        val lon = preferences.getDouble(LAST_LON) ?: return null
        val name = preferences.getString(LAST_LOCATION_NAME, null) ?: return null
        return LatLonWeatherLocation(
            name = name,
            lat = lat,
            lon = lon
        )
    }

    companion object {
        private const val LAT = "lat"
        private const val LON = "lon"
        private const val LOCATION_NAME = "location_name"
        private const val LAST_LAT = "last_lat"
        private const val LAST_LON = "last_lon"
        private const val LAST_LOCATION_NAME = "last_location_name"
    }
}

data class LatLonWeatherLocation(
    override val name: String,
    val lat: Double,
    val lon: Double
) : WeatherLocation