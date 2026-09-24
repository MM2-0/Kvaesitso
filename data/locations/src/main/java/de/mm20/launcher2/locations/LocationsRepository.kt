package de.mm20.launcher2.locations

import android.content.Context
import de.mm20.launcher2.devicepose.DevicePoseProvider
import de.mm20.launcher2.locations.providers.PluginLocationProvider
import de.mm20.launcher2.locations.providers.openstreetmaps.OsmLocationProvider
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.time.Duration.Companion.seconds

internal class LocationsRepository(
    private val context: Context,
    private val settings: LocationSearchSettings,
    private val poseProvider: DevicePoseProvider,
    private val permissionsManager: PermissionsManager,
) : SearchableRepository<Location> {

    @OptIn(FlowPreview::class)
    override fun search(
        query: String,
        allowNetwork: Boolean
    ): Flow<ImmutableList<Location>> {
        if (query.isBlank() || query.length <= 1) {
            return flowOf(persistentListOf())
        }
        return combineTransform(
            poseProvider
                .getLocation(minTimeMs = 2000, minDistanceM = 50.0f)
                // 1st location: lastCachedLocation of poseProvider, if available
                // 2nd location: LocationManager.getLastKnownLocation(), if available and better than lastCachedLocation
                // 3rd location: live location from LocationManager.requestLocationUpdates() that is better than any of the previous
                .take(3)
                // only request locations for 30 seconds
                .timeout(30.seconds),
            permissionsManager.hasPermission(PermissionGroup.Location),
            settings.data
        ) { userLocation, hasPermission, settingsData ->
            emit(persistentListOf())

            if (!hasPermission || settingsData.providers.isEmpty()) {
                return@combineTransform
            }

            val providers = settingsData.providers.map {
                when (it) {
                    "openstreetmaps" -> OsmLocationProvider(context, settings)
                    else -> PluginLocationProvider(context, it)
                }
            }

            supervisorScope {
                val result = MutableStateFlow(persistentListOf<Location>())

                for (provider in providers) {
                    launch {
                        val r = provider.search(
                            query,
                            userLocation,
                            allowNetwork,
                            settingsData.searchRadius,
                            settingsData.hideUncategorized
                        )
                        result.update {
                            (it + r).toPersistentList()
                        }
                    }
                }
                emitAll(result)
            }
        }
    }
}