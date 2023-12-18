package de.mm20.launcher2.weather.settings

import kotlinx.serialization.Serializable


@Serializable
data class LatLon(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class ProviderSettings(
    val lastUpdate: Long = 0,
    val locationId: String? = null,
    val locationName: String? = null,
)

@Serializable
data class WeatherSettingsData(
    val provider: String = "metno",
    val autoLocation: Boolean = true,
    val location: LatLon? = null,
    val locationName: String? = null,
    val lastLocation: LatLon? = null,
    val providerSettings: Map<String, ProviderSettings>
)