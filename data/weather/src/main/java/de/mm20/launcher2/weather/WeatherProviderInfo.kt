package de.mm20.launcher2.weather

data class WeatherProviderInfo(
    val id: String,
    val name: String,
    val managedLocation: Boolean = false,
)