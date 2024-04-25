package de.mm20.launcher2.locations

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.devicepose.DevicePoseProvider
import de.mm20.launcher2.locations.providers.PluginLocationProvider
import de.mm20.launcher2.locations.providers.openstreetmaps.OsmLocationProvider
import de.mm20.launcher2.locations.providers.openstreetmaps.OverpassApi
import de.mm20.launcher2.locations.providers.openstreetmaps.OverpassQueryConverterFactory
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

internal class LocationsRepository(
    private val context: Context,
    private val osmLocationProvider: OsmLocationProvider,
    private val settings: LocationSearchSettings,
    private val poseProvider: DevicePoseProvider,
    private val permissionsManager: PermissionsManager,
) : SearchableRepository<Location> {

    override fun search(
        query: String,
        allowNetwork: Boolean
    ): Flow<ImmutableList<Location>> = channelFlow {
        if (query.isBlank()) {
            send(persistentListOf())
            return@channelFlow
        }

        settings.enabledProviders.collectLatest {

            val providers = it.map {
                when (it) {
                    "openstreetmaps" -> osmLocationProvider
                    else -> PluginLocationProvider(context, it)
                }
            }

            if (providers.isEmpty()) {
                send(persistentListOf())
                return@collectLatest
            }

            settings.searchRadius.collectLatest { searchRadius ->
                settings.hideUncategorized.collectLatest { hideUncategorized ->
                    permissionsManager.hasPermission(PermissionGroup.Location)
                        .collectLatest { locationPermission ->

                            val userLocation =
                                if (!locationPermission) null else poseProvider.getLocation()
                                    .firstOrNull()
                                    ?: poseProvider.lastLocation

                            val results = mutableListOf<Location>()
                            for (provider in providers) {
                                results.addAll(
                                    provider.search(
                                        query,
                                        userLocation,
                                        allowNetwork,
                                        locationPermission,
                                        searchRadius,
                                        hideUncategorized
                                    )
                                )
                                send(results.toImmutableList())
                            }
                        }
                }
            }
        }
    }
}