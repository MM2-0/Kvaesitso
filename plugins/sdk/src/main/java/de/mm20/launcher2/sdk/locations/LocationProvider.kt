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
            LocationColumns.Id.set(it.id)
            LocationColumns.Label.set(it.label)
            LocationColumns.Latitude.set(it.latitude)
            LocationColumns.Longitude.set(it.longitude)
            LocationColumns.FixMeUrl.set(it.fixMeUrl)
            LocationColumns.Icon.set(it.icon?.name)
            LocationColumns.Category.set(it.category)
            LocationColumns.Address.set(it.address)
            LocationColumns.OpeningSchedule.set(it.openingSchedule)
            LocationColumns.WebsiteUrl.set(it.websiteUrl)
            LocationColumns.PhoneNumber.set(it.phoneNumber)
            LocationColumns.UserRating.set(it.userRating)
            LocationColumns.Departures.set(it.departures)
            LocationColumns.Attribution.set(it.attribution)
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