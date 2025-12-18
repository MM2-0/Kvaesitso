package de.mm20.launcher2.weather

import android.content.Context
import android.location.Geocoder
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.formatToString
import de.mm20.launcher2.preferences.weather.WeatherLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

internal abstract class GeocoderWeatherProvider(
    private val context: Context,
): WeatherProvider {
    override suspend fun findLocation(query: String): List<WeatherLocation> {
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
                } catch (e: Exception) {
                    CrashReporter.logException(e)
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

    internal suspend fun getLocationName(lat: Double, lon: Double): String {
        if (!Geocoder.isPresent()) return formatLatLon(lat, lon)
        return withContext(Dispatchers.IO) {
            try {
                Geocoder(context).getFromLocation(lat, lon, 1)
                    ?.firstOrNull()
                    ?.formatToString() ?: formatLatLon(lat, lon)
            } catch (e: Exception) {
                CrashReporter.logException(e)
                formatLatLon(lat, lon)
            }
        }
    }

    internal fun formatLatLon(lat: Double, lon: Double): String {
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
}