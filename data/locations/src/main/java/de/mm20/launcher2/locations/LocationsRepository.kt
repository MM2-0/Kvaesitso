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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class LocationsRepository(
    private val context: Context,
    private val settings: LocationSearchSettings,
    private val poseProvider: DevicePoseProvider,
    private val permissionsManager: PermissionsManager,
) : SearchableRepository<Location> {

    @OptIn(FlowPreview::class)
    override fun search(
        query: String
    ): Flow<ImmutableList<Location>> {
        if (query.isBlank()) {
            return flowOf(persistentListOf())
        }

        val hasPermission = permissionsManager.hasPermission(PermissionGroup.Location)

        return combineTransform(
            poseProvider.getLocation(minDistanceM = 50.0f),
            settings.data,
            hasPermission
        ) { userLocation, settingsData, permission ->
            emit(persistentListOf())
            if (!permission || settingsData.providers.isEmpty()) {
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