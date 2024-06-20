package de.mm20.launcher2.locations.providers

import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.UpdateResult

internal typealias AndroidLocation = android.location.Location

internal interface LocationProvider<TId> {
    suspend fun search(
        query: String,
        userLocation: AndroidLocation,
        allowNetwork: Boolean,
        searchRadiusMeters: Int,
        hideUncategorized: Boolean
    ): List<Location>
}