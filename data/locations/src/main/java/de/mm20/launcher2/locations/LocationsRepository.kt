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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

internal class LocationsRepository(
    private val context: Context,
    private val settings: LocationSearchSettings,
    private val poseProvider: DevicePoseProvider,
    private val permissionsManager: PermissionsManager,
) : SearchableRepository<Location> {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private val httpClient = OkHttpClient()

    private val overpassService by lazy {
        settings.overpassUrl.map {
            try {
                Retrofit.Builder()
                    .client(httpClient)
                    .baseUrl(it.takeIf { it.isNotBlank() }
                        ?: LocationSearchSettings.DefaultOverpassUrl)
                    .addConverterFactory(OverpassQueryConverterFactory())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(OverpassApi::class.java)
            } catch (e: Exception) {
                CrashReporter.logException(e)
                null
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)
    }

    override fun search(
        query: String,
        allowNetwork: Boolean
    ) = channelFlow {
        if (query.isBlank()) {
            send(persistentListOf())
            return@channelFlow
        }

        combine(
            settings.enabledProviders,
            settings.searchRadius,
            settings.hideUncategorized,
            permissionsManager.hasPermission(PermissionGroup.Location)
        ) { providers, searchRadius, hideUncategorized, locationPermission ->
            val providers = providers.map {
                when (it) {
                    "openstreetmaps" -> OsmLocationProvider(
                        overpassService,
                        poseProvider,
                        scope,
                        httpClient.dispatcher
                    )

                    else -> PluginLocationProvider(it, poseProvider, permissionsManager)
                }
            }

            if (providers.isEmpty()) {
                send(persistentListOf())
                return@combine
            }

            val results = mutableListOf<Location>()
            for (provider in providers) {
                results.addAll(
                    provider.search(
                        query,
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