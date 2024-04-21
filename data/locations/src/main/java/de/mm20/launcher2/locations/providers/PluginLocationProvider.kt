package de.mm20.launcher2.locations.providers

import de.mm20.launcher2.devicepose.DevicePoseProvider
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.UpdateResult

internal class PluginLocationProvider(
    private val pluginAuthority: String,
    private val poseProvider: DevicePoseProvider,
    private val permissionsManager: PermissionsManager,
): LocationProvider {
    override suspend fun search(
        query: String,
        allowNetwork: Boolean,
        hasLocationPermission: Boolean,
        searchRadiusMeters: Int,
        hideUncategorized: Boolean
    ): List<Location> {
        TODO()
    }
}