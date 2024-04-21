package de.mm20.launcher2.locations.providers

import de.mm20.launcher2.search.Location

internal interface LocationProvider {
    suspend fun search(
        query: String,
        allowNetwork: Boolean,
        hasLocationPermission: Boolean,
        searchRadiusMeters: Int,
        hideUncategorized: Boolean
    ): List<Location>
}