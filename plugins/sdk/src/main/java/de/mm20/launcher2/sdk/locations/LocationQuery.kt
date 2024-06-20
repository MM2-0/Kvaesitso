package de.mm20.launcher2.sdk.locations

data class LocationQuery(
    val query: String,
    val userLatitude: Double,
    val userLongitude: Double,
    val searchRadius: Long,
)
