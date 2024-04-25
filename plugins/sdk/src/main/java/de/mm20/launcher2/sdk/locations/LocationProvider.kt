package de.mm20.launcher2.sdk.locations

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.contracts.LocationPluginContract
import de.mm20.launcher2.sdk.base.BasePluginProvider
import de.mm20.launcher2.sdk.base.QueryPluginProvider
import de.mm20.launcher2.sdk.utils.launchWithCancellationSignal
import org.json.JSONArray
import org.json.JSONObject
import java.time.format.DateTimeFormatter

private val LocalTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

abstract class LocationPluginProvider : QueryPluginProvider<LocationQuery, Location>() {

    /**
     * Search for a location.
     * @param query Data to use in the query
     */
    abstract override suspend fun search(
        query: LocationQuery,
        allowNetwork: Boolean
    ): List<Location>

    /**
     * Get a location
     * @param id Provider-specific unique ID
     */
    abstract override suspend fun get(id: String): Location?

    final override fun getPluginType(): PluginType {
        return PluginType.LocationSearch
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?
    ): Cursor? {
        val context = context ?: return null
        checkPermissionOrThrow(context)
        val results = when (uri.pathSegments.first()) {
            LocationPluginContract.Paths.Search -> {
                val query =
                    uri.getQueryParameter(LocationPluginContract.SearchParams.Query) ?: return null
                val userLat =
                    uri.getQueryParameter(LocationPluginContract.SearchParams.UserLatitude)
                        ?.toDouble()
                val userLon =
                    uri.getQueryParameter(LocationPluginContract.SearchParams.UserLongitude)
                        ?.toDouble()
                val radius = uri.getQueryParameter(LocationPluginContract.SearchParams.SearchRadius)
                    ?.toLong() ?: return null
                val network =
                    uri.getQueryParameter(LocationPluginContract.SearchParams.AllowNetwork)
                        ?.toBoolean() ?: false
                launchWithCancellationSignal(cancellationSignal) {
                    search(
                        LocationQuery(
                            query, userLat, userLon, radius
                        ), network
                    )
                }
            }

            LocationPluginContract.Paths.Get -> {
                val id = uri.getQueryParameter(LocationPluginContract.GetParams.Id) ?: return null
                launchWithCancellationSignal(cancellationSignal) {
                    get(id)
                }?.let { listOf(it) } ?: emptyList()
            }

            else -> throw UnsupportedOperationException("This operation is not supported")
        }
        val cursor = createCursor(results.size)
        results.forEach { writeToCursor(cursor, it) }
        return cursor
    }

    private fun createCursor(capacity: Int): MatrixCursor {
        return MatrixCursor(
            arrayOf(
                LocationPluginContract.LocationColumns.Id,
                LocationPluginContract.LocationColumns.Label,
                LocationPluginContract.LocationColumns.Latitude,
                LocationPluginContract.LocationColumns.Longitude,
                LocationPluginContract.LocationColumns.FixMeUrl,
                LocationPluginContract.LocationColumns.Category,
                LocationPluginContract.LocationColumns.Street,
                LocationPluginContract.LocationColumns.HouseNumber,
                LocationPluginContract.LocationColumns.OpeningSchedule,
                LocationPluginContract.LocationColumns.WebsiteUrl,
                LocationPluginContract.LocationColumns.PhoneNumber,
                LocationPluginContract.LocationColumns.UserRating,
                LocationPluginContract.LocationColumns.Departures,
            ),
            capacity,
        )
    }

    private fun writeToCursor(cursor: MatrixCursor, item: Location) {
        cursor.addRow(
            arrayOf(
                item.id,
                item.label,
                item.latitude,
                item.longitude,
                item.fixMeUrl,
                item.category,
                item.street,
                item.houseNumber,
                item.openingSchedule?.let {
                    JSONObject().apply {
                        put("isTwentyFourSeven", it.isTwentyFourSeven)
                        put("openingHours", JSONArray().apply {
                            it.openingHours.forEach {
                                put(JSONObject().apply {
                                    put("dayOfWeek", it.dayOfWeek.value)
                                    put("startTime", it.startTime.format(LocalTimeFormatter))
                                    put("duration", it.duration.toMinutes())
                                })
                            }
                        })
                    }
                },
                item.websiteUrl,
                item.phoneNumber,
                item.userRating,
                item.departures?.let {
                    JSONArray().apply {
                        it.forEach { departure ->
                            put(JSONObject().apply {
                                put("time", departure.time.format(LocalTimeFormatter))
                                departure.delay?.let { put("delay", it.toMinutes()) }
                                put("line", departure.line)
                                departure.lastStop?.let { put("lastStop", it) }
                                departure.type?.let { put("type", it.name) }
                            })
                        }
                    }
                },
            )
        )
    }
}