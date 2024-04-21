package de.mm20.launcher2.locations.providers

import de.mm20.launcher2.search.Location

internal typealias AndroidLocation = android.location.Location

internal interface LocationProvider {
    suspend fun search(
        query: String,
        userLocation: AndroidLocation?,
        allowNetwork: Boolean,
        hasLocationPermission: Boolean,
        searchRadiusMeters: Int,
        hideUncategorized: Boolean
    ): List<Location>
}