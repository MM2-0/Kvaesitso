package de.mm20.launcher2.sdk.locations

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.LocationPluginContract
import de.mm20.launcher2.plugin.contracts.LocationPluginContract.LocationColumns
import de.mm20.launcher2.plugin.contracts.cursorOf
import de.mm20.launcher2.sdk.base.QueryPluginProvider
import de.mm20.launcher2.serialization.Json
import kotlinx.serialization.encodeToString

abstract class LocationProvider(
    config: QueryPluginConfig,
) : QueryPluginProvider<LocationQuery, Location>(config) {

    private val json = Json.Lenient

    final override fun getPluginType(): PluginType {
        return PluginType.LocationSearch
    }

    override fun List<Location>.toCursor(): Cursor {
        return cursorOf(LocationColumns, this) {
            put(LocationColumns.Id, it.id)
            put(LocationColumns.Label, it.label)
            put(LocationColumns.Latitude, it.latitude)
            put(LocationColumns.Longitude, it.longitude)
            put(LocationColumns.FixMeUrl, it.fixMeUrl)
            put(LocationColumns.Icon, it.icon)
            put(LocationColumns.Category, it.category)
            put(LocationColumns.Address, it.address)
            put(LocationColumns.OpeningSchedule, it.openingSchedule)
            put(LocationColumns.WebsiteUrl, it.websiteUrl)
            put(LocationColumns.PhoneNumber, it.phoneNumber)
            put(LocationColumns.EmailAddress, it.emailAddress)
            put(LocationColumns.UserRating, it.userRating)
            put(LocationColumns.UserRatingCount, it.userRatingCount)
            put(LocationColumns.Departures, it.departures)
            put(LocationColumns.Attribution, it.attribution)
        }
    }

    override fun getQuery(uri: Uri): LocationQuery? {
        val query = uri.getQueryParameter(LocationPluginContract.SearchParams.Query) ?: return null
        val searchRadius = uri.getQueryParameter(LocationPluginContract.SearchParams.SearchRadius)?.toLongOrNull() ?: return null
        val lat = uri.getQueryParameter(LocationPluginContract.SearchParams.UserLatitude)?.toDoubleOrNull() ?: return null
        val lon = uri.getQueryParameter(LocationPluginContract.SearchParams.UserLongitude)?.toDoubleOrNull() ?: return null
        return LocationQuery(
            query = query,
            userLatitude = lat,
            userLongitude = lon,
            searchRadius = searchRadius,
        )
    }
}