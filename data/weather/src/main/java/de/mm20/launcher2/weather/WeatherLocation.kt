package de.mm20.launcher2.weather

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
}