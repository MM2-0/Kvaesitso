package de.mm20.launcher2.preferences.weather

import android.icu.util.LocaleData
import android.icu.util.ULocale
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.LatLon
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.ProviderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

sealed interface WeatherLocation {
    val name: String

    data class LatLon(
        override val name: String,
        val lat: Double,
        val lon: Double,
    ) : WeatherLocation

    data class Id(
        override val name: String,
        val locationId: String,
    ) : WeatherLocation

    data object Managed : WeatherLocation {
        override val name: String = "Managed by plugin"
    }
}

data class WeatherSettingsData(
    val provider: String = "metno",
    val autoLocation: Boolean = true,
    val location: LatLon? = null,
    val locationName: String? = null,
    val lastLocation: LatLon? = null,
    val lastUpdate: Long = 0L,
    val providerSettings: Map<String, ProviderSettings> = emptyMap(),
)

class WeatherSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) :
    Flow<WeatherSettingsData> by (
            launcherDataStore.data.map {
                WeatherSettingsData(
                    provider = it.weatherProvider,
                    autoLocation = it.weatherAutoLocation,
                    location = it.weatherLocation,
                    locationName = it.weatherLocationName,
                    lastLocation = it.weatherLastLocation,
                    lastUpdate = it.weatherLastUpdate,
                    providerSettings = it.weatherProviderSettings,
                )
            }.distinctUntilChanged()
            ) {

    val location = launcherDataStore.data.map {
        val providerSettings = it.weatherProviderSettings[it.weatherProvider]

        if (providerSettings?.managedLocation == true) {
            return@map WeatherLocation.Managed
        }
        val id = providerSettings?.locationId
        val name = providerSettings?.locationName

        if (id != null && name != null) {
            WeatherLocation.Id(name, id)
        } else if (it.weatherLocation != null && it.weatherLocationName != null) {
            WeatherLocation.LatLon(
                it.weatherLocationName,
                it.weatherLocation.lat,
                it.weatherLocation.lon
            )
        } else {
            null
        }
    }.distinctUntilChanged()

    val autoLocation = launcherDataStore.data.map { it.weatherAutoLocation }
        .distinctUntilChanged()

    fun setLocation(location: WeatherLocation) {
        launcherDataStore.update {
            val providerSettings =
                it.weatherProviderSettings.getOrDefault(it.weatherProvider, ProviderSettings())
            when (location) {
                is WeatherLocation.LatLon -> {
                    it.copy(
                        weatherLocation = LatLon(lat = location.lat, lon = location.lon),
                        weatherLocationName = location.name,
                        weatherLastUpdate = 0L,
                        weatherAutoLocation = false,
                        weatherProviderSettings = it.weatherProviderSettings.toMutableMap().apply {
                            put(
                                it.weatherProvider,
                                providerSettings.copy(
                                    locationId = null,
                                    locationName = null,
                                    managedLocation = false,
                                )
                            )
                        }
                    )
                }

                is WeatherLocation.Id -> {
                    it.copy(
                        weatherLocation = null,
                        weatherLocationName = null,
                        weatherAutoLocation = false,
                        weatherLastUpdate = 0L,
                        weatherProviderSettings = it.weatherProviderSettings.toMutableMap().apply {
                            put(
                                it.weatherProvider,
                                providerSettings.copy(
                                    locationId = location.locationId,
                                    locationName = location.name,
                                    managedLocation = false,
                                )
                            )
                        }
                    )
                }

                is WeatherLocation.Managed -> {
                    it.copy(
                        weatherLocation = null,
                        weatherLocationName = null,
                        weatherAutoLocation = true,
                        weatherLastUpdate = 0L,
                        weatherProviderSettings = it.weatherProviderSettings.toMutableMap().apply {
                            put(
                                it.weatherProvider,
                                providerSettings.copy(
                                    locationId = null,
                                    locationName = null,
                                    managedLocation = true,
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    fun setLastLocation(location: LatLon) {
        launcherDataStore.update {
            it.copy(
                weatherLastLocation = location,
            )
        }
    }

    val lastUpdate = launcherDataStore.data.map { it.weatherLastUpdate }
        .distinctUntilChanged()

    fun setLastUpdate(lastUpdate: Long) {
        launcherDataStore.update {
            it.copy(weatherLastUpdate = lastUpdate)
        }
    }

    val providerId = launcherDataStore.data.map { it.weatherProvider }
        .distinctUntilChanged()

    fun setProvider(provider: String) {
        launcherDataStore.update {
            it.copy(
                weatherProvider = provider,
                weatherLastUpdate = 0L,
            )
        }
    }

    fun setAutoLocation(autoLocation: Boolean) {
        launcherDataStore.update {
            it.copy(
                weatherAutoLocation = autoLocation,
                weatherLastUpdate = 0L,
            )
        }
    }

    val measurementSystem = launcherDataStore.data.map {
        it.localeMeasurementSystem
    }.distinctUntilChanged()

    val timeFormat = launcherDataStore.data.map {
        it.localeTimeFormat
    }.distinctUntilChanged()
}