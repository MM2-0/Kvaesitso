package de.mm20.launcher2.weather

import android.content.Context
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.preferences.WeatherProviders
import de.mm20.launcher2.weather.here.HereProvider
import de.mm20.launcher2.weather.metno.MetNoProvider
import de.mm20.launcher2.weather.openweathermap.OpenWeatherMapProvider

abstract class WeatherProvider {

    abstract val supportsAutoLocation: Boolean

    abstract val supportsManualLocation: Boolean

    abstract var autoLocation: Boolean

    abstract suspend fun fetchNewWeatherData(): List<Forecast>?

    abstract fun isUpdateRequired(): Boolean

    abstract fun getLastUpdate(): Long

    /**
     * Lookup a location based on a string query.
     * @param query the location to lookup
     * @return a list of Pair<Any?,String> with provider specific data of that location and its
     * display name
     */
    abstract suspend fun lookupLocation(query: String): List<Pair<Any?, String>>

    abstract fun setLocation(locationId: Any?, locationName: String)

    abstract fun isAvailable(): Boolean

    abstract val name: String

    companion object {

        fun getInstance(context: Context): WeatherProvider? {
            return when (LauncherPreferences.instance.weatherProvider) {
                WeatherProviders.OPENWEATHERMAP -> OpenWeatherMapProvider(context)
                WeatherProviders.HERE -> HereProvider(context)
                else -> MetNoProvider(context)
            }.takeIf { it.isAvailable() }
        }
    }

    abstract fun getLastLocation(): String
    abstract fun resetLastUpdate()
}