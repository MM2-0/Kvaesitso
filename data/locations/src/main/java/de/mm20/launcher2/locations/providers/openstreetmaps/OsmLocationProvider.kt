package de.mm20.launcher2.locations.providers.openstreetmaps

import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.devicepose.DevicePoseProvider
import de.mm20.launcher2.locations.providers.LocationProvider
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.LocationCategory
import de.mm20.launcher2.search.UpdateResult
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import retrofit2.HttpException
import java.net.UnknownHostException

internal class OsmLocationProvider(
    private val overpassApi: StateFlow<OverpassApi?>,
    private val poseProvider: DevicePoseProvider,
    private val scope: CoroutineScope,
    private val dispatcher: Dispatcher,
) : LocationProvider {

    internal suspend fun update(
        id: Long
    ): UpdateResult<Location> = overpassApi.first()?.runCatching {
        this.search(
            OverpassIdQuery(
                id = id
            )
        ).let {
            OsmLocation.fromOverpassResponse(it)
        }.first().apply {
            updatedSelf = { update(id) }
        }
    }?.fold(
        onSuccess = { UpdateResult.Success(it) },
        onFailure = {
            when (it) {
                is CancellationException, is UnknownHostException -> {
                    // network
                    UpdateResult.TemporarilyUnavailable(it)
                }

                is HttpException -> when (it.code()) {
                    in 400..499 -> UpdateResult.PermanentlyUnavailable(it)
                    else -> UpdateResult.TemporarilyUnavailable(it)
                }

                is NoSuchElementException -> {
                    // empty response
                    UpdateResult.PermanentlyUnavailable(it)
                }

                else -> {
                    if (it is Exception) {
                        CrashReporter.logException(it)
                    }
                    UpdateResult.TemporarilyUnavailable(it)
                }
            }
        }
    ) ?: let {
        Log.e("OsmProvider", "overpassApi was not initialized")
        UpdateResult.TemporarilyUnavailable()
    }

    override suspend fun search(
        query: String,
        allowNetwork: Boolean,
        hasLocationPermission: Boolean,
        searchRadiusMeters: Int,
        hideUncategorized: Boolean,
    ): List<Location> {
        // values higher than 2 might block searches for "dm"
        // (Drogerie Markt, a problem specific to germany, but probably also relevant for other countries)
        if (!allowNetwork || !hasLocationPermission || query.length < 2)
            return emptyList()


        val userLocation =
            poseProvider.getLocation().firstOrNull() ?: poseProvider.lastLocation
            ?: return emptyList()

        withContext(Dispatchers.IO) {
            dispatcher.cancelAll()
        }

        suspend fun searchByTag(tag: String): OverpassResponse? =
            overpassApi.first()?.runCatching {
                this.search(
                    OverpassFuzzyRadiusQuery(
                        tag = tag,
                        query = query,
                        radius = searchRadiusMeters,
                        latitude = userLocation.latitude,
                        longitude = userLocation.longitude,
                    )
                )
            }?.onFailure {
                if (it !is HttpException && it !is CancellationException) {
                    Log.e("OsmLocationProvider", "Failed to search for $tag: $query", it)
                }
            }?.getOrNull()

        val result = awaitAll(
            // optionally query by "amenity" or "shop" here
            // if we want to make searching for locations fuzzier
            // however, this would not account for localized queries like "Bäcker" (shop:bakery)
            scope.async { searchByTag("name") },
            scope.async { searchByTag("brand") },
        ).flatMap {
            it?.let {
                OsmLocation.fromOverpassResponse(it)
            } ?: emptyList()
        }

        return result
            .asSequence()
            .filter {
                !hideUncategorized || (it.category != null && it.category != LocationCategory.OTHER)
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
            .sortedBy {
                it.distanceTo(userLocation)
            }
            .take(7)
            .toImmutableList()
    }
}
