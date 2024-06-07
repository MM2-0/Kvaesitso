package de.mm20.launcher2.locations.providers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.plugin.PluginApi
import de.mm20.launcher2.plugin.QueryPluginApi
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.LocationPluginContract
import de.mm20.launcher2.plugin.contracts.LocationPluginContract.LocationColumns
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.plugin.data.set
import de.mm20.launcher2.plugin.data.withColumns
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.UpdateResult

internal class PluginLocationProvider(
    private val context: Context,
    private val pluginAuthority: String
) : QueryPluginApi<Triple<String, AndroidLocation, Int>, PluginLocation>(
    context,
    pluginAuthority
), LocationProvider<String> {

    override fun PluginLocation.getId(): String {
        return id
    }

    override suspend fun search(
        query: String,
        userLocation: AndroidLocation,
        allowNetwork: Boolean,
        searchRadiusMeters: Int,
        hideUncategorized: Boolean
    ): List<Location> {
        return search(
            query = Triple(query, userLocation, searchRadiusMeters),
            allowNetwork = allowNetwork,
        )
    }

    override fun Uri.Builder.appendQueryParameters(query: Triple<String, AndroidLocation, Int>): Uri.Builder {
        return apply {
            appendQueryParameter(SearchPluginContract.Params.Query, query.first)
            appendQueryParameter(
                LocationPluginContract.Params.UserLatitude,
                query.second.latitude.toString()
            )
            appendQueryParameter(
                LocationPluginContract.Params.UserLongitude,
                query.second.longitude.toString()
            )
            appendQueryParameter(LocationPluginContract.Params.SearchRadius, query.third.toString())
        }
    }

    private fun fromCursor(cursor: Cursor): List<Location>? {
        val config = getPluginConfig()

        if (config == null) {
            Log.e("MM20", "Plugin ${pluginAuthority} returned null config")
            cursor.close()
            return null
        }

        val results = mutableListOf<Location>()
        cursor.withColumns(LocationColumns) {
            while (cursor.moveToNext()) {
                val id = cursor[LocationColumns.Id] ?: continue
                results.add(
                    PluginLocation(
                        id = id,
                        label = cursor[LocationColumns.Label] ?: continue,
                        latitude = cursor[LocationColumns.Latitude] ?: continue,
                        longitude = cursor[LocationColumns.Longitude] ?: continue,
                        fixMeUrl = cursor[LocationColumns.FixMeUrl],
                        icon = cursor[LocationColumns.Icon],
                        category = cursor[LocationColumns.Category],
                        address = cursor[LocationColumns.Address],
                        openingSchedule = cursor[LocationColumns.OpeningSchedule],
                        websiteUrl = cursor[LocationColumns.WebsiteUrl],
                        phoneNumber = cursor[LocationColumns.PhoneNumber],
                        emailAddress = cursor[LocationColumns.EmailAddress],
                        userRating = cursor[LocationColumns.UserRating],
                        userRatingCount = cursor[LocationColumns.UserRatingCount],
                        departures = cursor[LocationColumns.Departures],
                        attribution = cursor[LocationColumns.Attribution],
                        authority = pluginAuthority,
                        updatedSelf = null,
                        timestamp = System.currentTimeMillis(),
                        storageStrategy = config.storageStrategy,
                    )
                )
            }
        }

        return results
    }

    private fun getPluginConfig(): QueryPluginConfig? {
        return PluginApi(pluginAuthority, context.contentResolver).getConfig()?.let {
            QueryPluginConfig(it)
        }
    }

    override fun Cursor.getData(): List<PluginLocation>? {
        val config = getPluginConfig()
        val cursor = this

        if (config == null) {
            Log.e("MM20", "Plugin ${pluginAuthority} returned null config")
            cursor.close()
            return null
        }

        val results = mutableListOf<PluginLocation>()
        cursor.withColumns(LocationColumns) {
            while (cursor.moveToNext()) {
                val id = cursor[LocationColumns.Id] ?: continue
                results.add(
                    PluginLocation(
                        id = id,
                        label = cursor[LocationColumns.Label] ?: continue,
                        latitude = cursor[LocationColumns.Latitude] ?: continue,
                        longitude = cursor[LocationColumns.Longitude] ?: continue,
                        fixMeUrl = cursor[LocationColumns.FixMeUrl],
                        icon = cursor[LocationColumns.Icon],
                        category = cursor[LocationColumns.Category],
                        address = cursor[LocationColumns.Address],
                        openingSchedule = cursor[LocationColumns.OpeningSchedule],
                        websiteUrl = cursor[LocationColumns.WebsiteUrl],
                        phoneNumber = cursor[LocationColumns.PhoneNumber],
                        emailAddress = cursor[LocationColumns.EmailAddress],
                        userRating = cursor[LocationColumns.UserRating],
                        userRatingCount = cursor[LocationColumns.UserRatingCount],
                        departures = cursor[LocationColumns.Departures],
                        attribution = cursor[LocationColumns.Attribution],
                        authority = pluginAuthority,
                        updatedSelf = null,
                        timestamp = System.currentTimeMillis(),
                        storageStrategy = config.storageStrategy,
                    )
                )
            }
        }
        return results
    }

    override fun PluginLocation.toBundle(): Bundle {
        return Bundle().apply {
            set(LocationColumns.Id, id)
            set(LocationColumns.Label, label)
            set(LocationColumns.Latitude, latitude)
            set(LocationColumns.Longitude, longitude)
            set(LocationColumns.FixMeUrl, fixMeUrl)
            set(LocationColumns.Icon, icon)
            set(LocationColumns.Category, category)
            set(LocationColumns.Address, address)
            set(LocationColumns.OpeningSchedule, openingSchedule)
            set(LocationColumns.WebsiteUrl, websiteUrl)
            set(LocationColumns.PhoneNumber, phoneNumber)
            set(LocationColumns.EmailAddress, emailAddress)
            set(LocationColumns.UserRating, userRating)
            set(LocationColumns.UserRatingCount, userRatingCount)
            set(LocationColumns.Departures, departures)
            set(LocationColumns.Attribution, attribution)
        }
    }
}