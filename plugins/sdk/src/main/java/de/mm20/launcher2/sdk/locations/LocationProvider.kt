package de.mm20.launcher2.sdk.locations

import android.database.MatrixCursor
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.config.SearchPluginConfig
import de.mm20.launcher2.plugin.contracts.LocationPluginContract
import de.mm20.launcher2.sdk.base.SearchPluginProvider
import org.json.JSONArray
import org.json.JSONObject
import java.time.format.DateTimeFormatter

private val LocalTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

abstract class LocationProvider(
    config: SearchPluginConfig,
) : SearchPluginProvider<Location>(config) {
    abstract override suspend fun search(query: String, allowNetwork: Boolean): List<Location>

    final override fun getPluginType(): PluginType {
        return PluginType.LocationSearch
    }

    override fun createCursor(capacity: Int): MatrixCursor {
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

    override fun writeToCursor(cursor: MatrixCursor, item: Location) {
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