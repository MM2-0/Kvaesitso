package de.mm20.launcher2.locations.providers.openstreetmaps

import android.content.Context
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.stripEndOrNull
import de.mm20.launcher2.ktx.stripStartOrNull
import de.mm20.launcher2.locations.providers.AndroidLocation
import de.mm20.launcher2.locations.providers.LocationProvider
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.UpdateResult
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.UnknownHostException
import de.mm20.launcher2.openstreetmaps.R
import de.mm20.launcher2.search.ResultScore
import java.lang.reflect.Field

private val Scope = CoroutineScope(Job() + Dispatchers.IO)
private val HttpClient = OkHttpClient()

internal class OsmLocationProvider(
    private val context: Context,
    settings: LocationSearchSettings
) : LocationProvider<Long> {

    private val overpassApi = settings.overpassUrl.map {
        try {
            Retrofit.Builder()
                .client(HttpClient)
                .baseUrl(it?.takeIf { it.isNotBlank() }
                    ?: LocationSearchSettings.DefaultOverpassUrl)
                .addConverterFactory(OverpassQueryConverterFactory())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OverpassApi::class.java)
        } catch (e: Exception) {
            CrashReporter.logException(e)
            null
        }
    }.stateIn(Scope, SharingStarted.Eagerly, null)


    suspend fun update(
        id: Long
    ): UpdateResult<Location> = overpassApi.first()?.runCatching {
        this.search(
            OverpassIdQuery(
                id = id
            )
        ).let {
            OsmLocation.fromOverpassResponse(it, context)
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
        userLocation: AndroidLocation,
        allowNetwork: Boolean,
        searchRadiusMeters: Int,
        hideUncategorized: Boolean,
    ): List<Location> {
        if (!allowNetwork || query.length < 2) {
            return emptyList()
        }

        withContext(Dispatchers.IO) {
            HttpClient.dispatcher.cancelAll()
        }

        return overpassApi.first()?.runCatching {
            search(
                OverpassFuzzyRadiusQuery(
                    mutableListOf(
                        QueryableTags(listOf("name" to query, "brand" to query))
                    ).apply {
                        delocalizeToQueryableTags(query)?.let { add(it) }
                    },
                    radius = searchRadiusMeters,
                    latitude = userLocation.latitude,
                    longitude = userLocation.longitude
                )
            )
        }?.onFailure {
            if (it !is HttpException && it !is CancellationException) {
                Log.e("OsmLocationProvider", "Failed to search for: $query", it)
            }
        }?.getOrNull()?.let {
            OsmLocation.fromOverpassResponse(it, context)
        }?.asSequence()?.filter {
            (!hideUncategorized || (it.category != null)) && it.distanceTo(userLocation) < searchRadiusMeters
        }?.groupBy {
            it.label.lowercase()
        }?.flatMap { (_, duplicates) ->
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
        }?.sortedBy {
            it.distanceTo(userLocation)
        }?.take(7)?.toImmutableList() ?: emptyList()
    }

    private data class PoiCategory(
        val field: Field,
        val queryableTags: QueryableTags
    )

    private val poiCategories by lazy {
        R.string::class.java.declaredFields.mapNotNull { field ->
            field.name.stripStartOrNull("poi_category_")?.let { category ->
                PoiCategory(
                    field,
                    category.stripEndOrNull("_restaurant")?.let { cuisine ->
                        if (cuisine.isNotBlank())
                            QueryableTags(
                                listOf(
                                    "amenity" to "restaurant",
                                    "cuisine" to cuisine
                                ),
                                intersection = true
                            )
                        else
                            QueryableTags(listOf("amenity" to "restaurant"))
                    } ?: QueryableTags(listOf("amenity" to category, "shop" to category))
                )
            }
        }
    }

    private fun delocalizeToQueryableTags(localizedQuery: String): QueryableTags? =
        poiCategories.asSequence().map { (field, queryableTags) ->
            ResultScore(
                localizedQuery,
                primaryFields = listOf(context.getString(field.getInt(null)))
            ) to queryableTags
        }.maxByOrNull {
            it.first
        }?.takeIf { it.first.score > 0.8f }?.second

}
