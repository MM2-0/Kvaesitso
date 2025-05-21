package de.mm20.launcher2.locations.providers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import de.mm20.launcher2.plugin.QueryPluginApi
import de.mm20.launcher2.plugin.contracts.LocationPluginContract
import de.mm20.launcher2.plugin.contracts.LocationPluginContract.LocationColumns
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.plugin.data.set
import de.mm20.launcher2.plugin.data.withColumns
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.asUpdateResult

internal class PluginLocationProvider(
    context: Context,
    private val pluginAuthority: String
) : QueryPluginApi<Triple<String, AndroidLocation, Int>, PluginLocation>(
    context,
    pluginAuthority
), LocationProvider<String> {

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

    override fun Cursor.getData(): List<PluginLocation>? {
        val config = getConfig()
        val cursor = this

        if (config == null) {
            Log.e("MM20", "Plugin ${pluginAuthority} returned null config")
            cursor.close()
            return null
        }

        val results = mutableListOf<PluginLocation>()
        val timestamp = System.currentTimeMillis()
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
                        acceptedPaymentMethods = cursor[LocationColumns.AcceptedPaymentMethods],
                        authority = pluginAuthority,
                        updatedSelf = {
                            if (it !is PluginLocation) UpdateResult.TemporarilyUnavailable()
                            else refresh(it, timestamp).asUpdateResult()
                        },
                        timestamp = timestamp,
                        storageStrategy = config.storageStrategy,
                    )
                )
            }
        }
        cursor.close()
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
            set(LocationColumns.AcceptedPaymentMethods, acceptedPaymentMethods)
        }
    }
}