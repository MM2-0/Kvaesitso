package de.mm20.launcher2.locations.providers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.util.Log
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.plugin.PluginApi
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.contracts.LocationPluginContract
import de.mm20.launcher2.plugin.contracts.LocationPluginContract.LocationColumns
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.plugin.contracts.withColumns
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.serialization.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
        val lang = context.resources.configuration.locales.get(0).language
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
            .appendQueryParameter(
                SearchPluginContract.Paths.LangParam,
                lang
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
            .path(SearchPluginContract.Paths.Root)
            .appendPath(id)
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
        return PluginApi(pluginAuthority, context.contentResolver).getSearchPluginConfig()
    }
}