package de.mm20.launcher2.weather

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import androidx.core.content.edit
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.checkPermission

abstract class WeatherProvider<T : WeatherLocation> {

    internal abstract val context: Context

    internal abstract val preferences: SharedPreferences

    var autoLocation: Boolean
        get() {
            return preferences.getBoolean(AUTO_LOCATION, true)
        }
        set(value) {
            preferences.edit {
                putBoolean(AUTO_LOCATION, value)
            }
        }


    suspend fun fetchNewWeatherData(): List<Forecast>? {
        val result: WeatherUpdateResult<T> = if (autoLocation) {
            val location = getCurrentLocation()
            if (location != null) {
                loadWeatherData(location.latitude, location.longitude) ?: return null
            } else {
                val lastLocation = getLastLocation() ?: return null
                loadWeatherData(lastLocation) ?: return null
            }
        } else {
            val setLocation = getLocation() ?: return null
            loadWeatherData(setLocation) ?: return null
        }
        saveLastLocation(result.location)
        setLastUpdate(System.currentTimeMillis())
        return result.forecasts
    }

    private fun getCurrentLocation(): Location? {
        val lm = context.getSystemService<LocationManager>()!!
        var location: Location? = null
        if (context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        if (location == null && context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        return location
    }

    internal abstract suspend fun loadWeatherData(location: T): WeatherUpdateResult<T>?
    internal abstract suspend fun loadWeatherData(lat: Double, lon: Double): WeatherUpdateResult<T>?

    abstract fun isUpdateRequired(): Boolean

    fun getLastUpdate(): Long {
        return preferences.getLong(LAST_UPDATE, 0)
    }

    private fun setLastUpdate(time: Long) {
        preferences.edit {
            putLong(LAST_UPDATE, time)
        }
    }

    /**
     * Lookup a location based on a string query.
     * @param query the location to lookup
     * @return a list of locations
     */
    abstract suspend fun lookupLocation(query: String): List<T>

    /**
     * @param location must be of type T
     */
    abstract fun setLocation(location: WeatherLocation?)
    abstract fun getLocation(): T?

    abstract fun isAvailable(): Boolean

    abstract val name: String

    abstract fun getLastLocation(): T?

    abstract fun saveLastLocation(location: T)

    fun resetLastUpdate() {
        preferences.edit {
            putLong(LAST_UPDATE, 0L)
        }
    }

    companion object {

        private const val LAST_UPDATE = "last_update"
        private const val AUTO_LOCATION = "auto_location"
    }
}

data class WeatherUpdateResult<T : WeatherLocation>(
    val forecasts: List<Forecast>,
    val location: T
)