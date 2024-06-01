package de.mm20.launcher2.sdk.locations

import android.database.MatrixCursor
import android.net.Uri
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.LocationPluginContract
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

    override fun createCursor(capacity: Int): MatrixCursor {
        return MatrixCursor(
            arrayOf(
                LocationPluginContract.LocationColumns.Id,
                LocationPluginContract.LocationColumns.Label,
                LocationPluginContract.LocationColumns.Latitude,
                LocationPluginContract.LocationColumns.Longitude,
                LocationPluginContract.LocationColumns.FixMeUrl,
                LocationPluginContract.LocationColumns.Icon,
                LocationPluginContract.LocationColumns.Category,
                LocationPluginContract.LocationColumns.Address,
                LocationPluginContract.LocationColumns.OpeningSchedule,
                LocationPluginContract.LocationColumns.WebsiteUrl,
                LocationPluginContract.LocationColumns.PhoneNumber,
                LocationPluginContract.LocationColumns.UserRating,
                LocationPluginContract.LocationColumns.Departures,
                LocationPluginContract.LocationColumns.Attribution,
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
                item.icon?.name,
                item.category,
                item.address?.let {
                    json.encodeToString(it)
                },
                item.openingSchedule?.let {
                    json.encodeToString(it)
                },
                item.websiteUrl,
                item.phoneNumber,
                item.userRating,
                item.departures?.let {
                    json.encodeToString(it)
                },
                item.attribution?.let {
                    json.encodeToString(it)
                },
            )
        )
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