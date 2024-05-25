package de.mm20.launcher2.locations.providers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.util.Log
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.decodeFromStringOrNull
import de.mm20.launcher2.locations.getOpeningSchedule
import de.mm20.launcher2.plugin.PluginApi
import de.mm20.launcher2.plugin.config.SearchPluginConfig
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.plugin.contracts.LocationPluginContract
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LocationCategory
import de.mm20.launcher2.serialization.Json
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

internal class PluginLocationProvider(
    private val context: Context,
    private val pluginAuthority: String
) : LocationProvider<String> {

    private val json = Json.Lenient

    override suspend fun search(
        query: String,
        userLocation: AndroidLocation?,
        allowNetwork: Boolean,
        hasLocationPermission: Boolean,
        searchRadiusMeters: Int,
        hideUncategorized: Boolean
    ): List<Location> = withContext(Dispatchers.IO) {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(pluginAuthority)
            .path(LocationPluginContract.Paths.Search)
            .appendQueryParameter(LocationPluginContract.SearchParams.Query, query)
            .appendQueryParameter(
                LocationPluginContract.SearchParams.AllowNetwork,
                allowNetwork.toString()
            )
            .appendQueryParameter(
                LocationPluginContract.SearchParams.UserLatitude,
                userLocation?.latitude.toString()
            )
            .appendQueryParameter(
                LocationPluginContract.SearchParams.UserLongitude,
                userLocation?.longitude.toString()
            )
            .appendQueryParameter(
                LocationPluginContract.SearchParams.SearchRadius,
                searchRadiusMeters.toString()
            )
            .build()
        val cancellationSignal = CancellationSignal()

        return@withContext suspendCancellableCoroutine {
            it.invokeOnCancellation {
                cancellationSignal.cancel()
            }
            val cursor = try {
                context.contentResolver.query(
                    uri,
                    null,
                    null,
                    cancellationSignal
                )
            } catch (e: Exception) {
                Log.e("MM20", "Plugin $pluginAuthority threw exception")
                CrashReporter.logException(e)
                it.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            if (cursor == null) {
                Log.e("MM20", "Plugin $pluginAuthority returned null cursor")
                it.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val results = fromCursor(cursor) ?: emptyList()
            it.resume(results)
        }
    }

    override suspend fun update(id: String): UpdateResult<Location> = withContext(Dispatchers.IO) {
        // TODO respect allowNetwork?
        val uri = Uri.Builder()
            .scheme("content")
            .authority(pluginAuthority)
            .path(LocationPluginContract.Paths.Get)
            .appendQueryParameter(LocationPluginContract.GetParams.Id, id)
            .build()

        val cancellationSignal = CancellationSignal()

        return@withContext suspendCancellableCoroutine {
            it.invokeOnCancellation {
                cancellationSignal.cancel()
            }
            val cursor = try {
                context.contentResolver.query(
                    uri,
                    null,
                    null,
                    cancellationSignal
                )
            } catch (e: Exception) {
                Log.e("MM20", "Plugin $pluginAuthority threw exception")
                CrashReporter.logException(e)
                it.resume(UpdateResult.TemporarilyUnavailable(e))
                return@suspendCancellableCoroutine
            }

            if (cursor == null) {
                Log.e("MM20", "Plugin $pluginAuthority returned null cursor")
                it.resume(UpdateResult.TemporarilyUnavailable())
                return@suspendCancellableCoroutine
            }

            val result = fromCursor(cursor)?.firstOrNull()

            if (result == null) {
                it.resume(UpdateResult.PermanentlyUnavailable())
                return@suspendCancellableCoroutine
            }

            it.resume(UpdateResult.Success(result))
        }
    }

    private fun fromCursor(cursor: Cursor): List<Location>? {
        val config = getPluginConfig()

        if (config == null) {
            Log.e("MM20", "Plugin ${pluginAuthority} returned null config")
            cursor.close()
            return null
        }

        val idIdx =
            cursor.getColumnIndex(LocationPluginContract.LocationColumns.Id)
                .takeIf { it != -1 } ?: return null
        val labelIdx =
            cursor.getColumnIndex(LocationPluginContract.LocationColumns.Label)
                .takeIf { it != -1 } ?: return null
        val latitudeIdx = cursor.getColumnIndex(LocationPluginContract.LocationColumns.Latitude)
            .takeIf { it != -1 } ?: return null
        val longitudeIdx = cursor.getColumnIndex(LocationPluginContract.LocationColumns.Longitude)
            .takeIf { it != -1 } ?: return null
        val fixMeUrlIdx = cursor.getColumnIndex(LocationPluginContract.LocationColumns.FixMeUrl)
            .takeIf { it != -1 }
        val categoryIdy = cursor.getColumnIndex(LocationPluginContract.LocationColumns.Category)
            .takeIf { it != -1 }
        val streetIdx =
            cursor.getColumnIndex(LocationPluginContract.LocationColumns.Street).takeIf { it != -1 }
        val houseNumberIdx =
            cursor.getColumnIndex(LocationPluginContract.LocationColumns.HouseNumber)
                .takeIf { it != -1 }
        val openingScheduleIdx =
            cursor.getColumnIndex(LocationPluginContract.LocationColumns.OpeningSchedule)
                .takeIf { it != -1 }
        val websiteUrlIdx = cursor.getColumnIndex(LocationPluginContract.LocationColumns.WebsiteUrl)
            .takeIf { it != -1 }
        val phoneNumberIdx =
            cursor.getColumnIndex(LocationPluginContract.LocationColumns.PhoneNumber)
                .takeIf { it != -1 }
        val userRatingIdx = cursor.getColumnIndex(LocationPluginContract.LocationColumns.UserRating)
            .takeIf { it != -1 }
        val departuresIdx = cursor.getColumnIndex(LocationPluginContract.LocationColumns.Departures)
            .takeIf { it != -1 }

        val results = mutableListOf<Location>()
        while (cursor.moveToNext()) {
            val id = cursor.getString(idIdx)
            results.add(
                PluginLocation(
                    id = id,
                    label = cursor.getString(labelIdx),
                    latitude = cursor.getDouble(latitudeIdx),
                    longitude = cursor.getDouble(longitudeIdx),
                    fixMeUrl = fixMeUrlIdx?.let { cursor.getString(it) },
                    category = categoryIdy?.let {
                        LocationCategory.valueOfOrNull(
                            cursor.getString(it)
                        )
                    },
                    street = streetIdx?.let { cursor.getString(it) },
                    houseNumber = houseNumberIdx?.let { cursor.getString(it) },
                    openingSchedule = openingScheduleIdx?.let {
                        cursor.getStringOrNull(it)?.let {
                            getOpeningSchedule(JSONObject(it))
                        }
                    },
                    websiteUrl = websiteUrlIdx?.let { cursor.getString(it) },
                    phoneNumber = phoneNumberIdx?.let { cursor.getString(it) },
                    userRating = userRatingIdx?.let { cursor.getFloat(it) },
                    departures = departuresIdx?.let {
                        cursor.getStringOrNull(it)?.let {
                            json.decodeFromStringOrNull<ImmutableList<Departure>>(it)
                        }
                    },
                    authority = pluginAuthority,
                    updatedSelf = null,
                    timestamp = System.currentTimeMillis(),
                    storageStrategy = config.storageStrategy,
                )
            )
        }
        return results
    }

    private fun getPluginConfig(): SearchPluginConfig? {
        return PluginApi(pluginAuthority, context.contentResolver).getSearchPluginConfig()
    }
}