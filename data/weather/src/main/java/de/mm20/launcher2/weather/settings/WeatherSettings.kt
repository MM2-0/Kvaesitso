package de.mm20.launcher2.weather.settings

import android.content.Context
import de.mm20.launcher2.settings.BaseSettings
import de.mm20.launcher2.weather.WeatherLocation
import de.mm20.launcher2.weather.WeatherProviderInfo
import kotlinx.coroutines.flow.map

class WeatherSettings(
    private val context: Context,
) : BaseSettings<WeatherSettingsData>(
    context,
    "weather_settings.json",
    WeatherSettingsSerializer,
    emptyList(),
) {
    internal val data
        get() = context.dataStore.data

    val location = data.map {
        val providerSettings = it.providerSettings[it.provider]
        val id = providerSettings?.locationId
        val name = providerSettings?.locationName

        if (id != null && name != null) {
            WeatherLocation.Id(name, id)
        } else if (it.location != null && it.locationName != null) {
            WeatherLocation.LatLon(it.locationName, it.location.lat, it.location.lon)
        } else {
            null
        }
    }

    val autoLocation = data.map { it.autoLocation }

    fun setLocation(location: WeatherLocation) {
        updateData {
            val providerSettings =
                it.providerSettings.getOrDefault(it.provider, ProviderSettings())
            when (location) {
                is WeatherLocation.LatLon -> {
                    it.copy(
                        location = LatLon(lat = location.lat, lon = location.lon),
                        locationName = location.name,
                        lastUpdate = 0L,
                        autoLocation = false,
                        providerSettings = it.providerSettings.toMutableMap().apply {
                            put(
                                it.provider,
                                providerSettings.copy(
                                    locationId = null,
                                    locationName = null,
                                )
                            )
                        }
                    )
                }

                is WeatherLocation.Id -> {
                    it.copy(
                        location = null,
                        locationName = null,
                        autoLocation = false,
                        lastUpdate = 0L,
                        providerSettings = it.providerSettings.toMutableMap().apply {
                            put(
                                it.provider,
                                providerSettings.copy(
                                    locationId = location.locationId,
                                    locationName = location.name
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    fun setLastLocation(location: LatLon) {
        updateData {
            it.copy(
                lastLocation = location,
            )
        }
    }

    val lastUpdate = data.map { it.lastUpdate }

    fun setLastUpdate(lastUpdate: Long) {
        updateData {
            it.copy(lastUpdate = lastUpdate)
        }
    }

    val providerId = data.map { it.provider }

    fun setProvider(provider: WeatherProviderInfo) {
        setProviderId(provider.id)
    }

    fun setAutoLocation(autoLocation: Boolean) {
        updateData {
            it.copy(
                autoLocation = autoLocation,
                lastUpdate = 0L,
            )
        }
    }

    fun setProviderId(providerId: String) {
        updateData {
            it.copy(
                provider = providerId,
                lastUpdate = 0L,
            )
        }
    }
}