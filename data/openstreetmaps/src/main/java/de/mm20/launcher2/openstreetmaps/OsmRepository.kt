package de.mm20.launcher2.openstreetmaps

import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.devicepose.DevicePoseProvider
import de.mm20.launcher2.openstreetmaps.settings.LocationSearchSettings
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

internal class OsmRepository(
    private val settings: LocationSearchSettings,
    private val poseProvider: DevicePoseProvider,
    permissionsManager: PermissionsManager,
) : SearchableRepository<Location> {


    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val httpClient = OkHttpClient()
    private val overpassService = settings.overpassUrl.map {
        try {
            Retrofit.Builder()
                .client(httpClient)
                .baseUrl(it.takeIf { it.isNotBlank() } ?: LocationSearchSettings.DefaultOverpassUrl)
                .addConverterFactory(OverpassQueryConverterFactory())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OverpassApi::class.java)
        } catch (e: Exception) {
            CrashReporter.logException(e)
            null
        }
    }.stateIn(scope, SharingStarted.Lazily, null)

    private val hasLocationPermission = permissionsManager.hasPermission(PermissionGroup.Location)

    fun get(id: Long): Flow<Location> = flow {
        emit(searchForId(id))
    }

    private suspend fun searchForId(id: Long): OsmLocation {
        val overpassService =
            overpassService.first() ?: throw IllegalStateException("Overpass-service not available")

        return overpassService.search(
            OverpassIdQuery(
                id = id
            )
        ).let {
            OsmLocation.fromOverpassResponse(it)
        }.first()
    }

    override fun search(query: String): Flow<ImmutableList<OsmLocation>> = channelFlow {
        send(persistentListOf())

        // values higher than 2 might block searches for "dm"
        // (Drogerie Markt, a problem specific to germany, but probably also relevant for other countries)
        if (query.length < 2) return@channelFlow

        hasLocationPermission.collectLatest { locationPermission ->
            if (!locationPermission) return@collectLatest

            settings.data.collectLatest dataStore@{ settings ->
                if (!settings.enabled) return@dataStore

                val userLocation =
                    poseProvider.getLocation().firstOrNull() ?: poseProvider.lastLocation
                    ?: return@dataStore

                withContext(Dispatchers.IO) {
                    httpClient.dispatcher.cancelAll()
                }

                suspend fun searchByTag(tag: String): OverpassResponse? =
                    overpassService.first()?.runCatching {
                        this.search(
                            OverpassFuzzyRadiusQuery(
                                tag = tag,
                                query = query,
                                radius = settings.searchRadius,
                                latitude = userLocation.latitude,
                                longitude = userLocation.longitude,
                            )
                        )
                    }?.onFailure {
                        if (it !is HttpException && it !is CancellationException) {
                            Log.e("OsmRepository", "Failed to search for $tag: $query", it)
                        }
                    }?.getOrNull()

                val result = awaitAll(
                    // optionally query by "amenity" or "shop" here
                    // if we want to make searching for locations fuzzier
                    // however, this would not account for localized queries like "Bäcker" (shop:bakery)
                    async(this.coroutineContext) { searchByTag("name") },
                    async(this.coroutineContext) { searchByTag("brand") },
                ).flatMap {
                    it?.let {
                        OsmLocation.fromOverpassResponse(it)
                    } ?: emptyList()
                }

                if (result.isNotEmpty()) {
                    send(
                        result
                            .filter {
                                !settings.hideUncategorized || (it.category != null && it.category != LocationCategory.OTHER)
                            }
                            .groupBy {
                                it.label.lowercase()
                            }
                            .flatMap { (_, duplicates) ->
                                // deduplicate results with same labels, if
                                // - same category
                                // - distance is less than 100m
                                if (duplicates.size < 2) duplicates
                                else {
                                    val luckyFirst = duplicates.first()
                                    duplicates
                                        .drop(1)
                                        .filter {
                                            it.category != luckyFirst.category ||
                                                    it.distanceTo(luckyFirst) > 100.0
                                        } + luckyFirst
                                }
                            }
                            .toImmutableList()
                    )
                }
            }
        }
    }
}