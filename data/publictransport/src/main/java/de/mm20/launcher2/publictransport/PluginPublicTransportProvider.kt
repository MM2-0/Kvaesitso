package de.mm20.launcher2.publictransport

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getStringOrNull
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.plugin.contracts.PublicTransportPluginContract
import de.mm20.launcher2.plugin.contracts.SearchPluginContract
import de.mm20.launcher2.search.Departure
import de.mm20.launcher2.search.LineType
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.PublicTransportStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import kotlin.coroutines.resume

class PluginPublicTransportProvider(
    private val context: Context,
    private val pluginAuthority: String,
) {
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun search(query: List<Location>, allowNetwork: Boolean): List<PublicTransportStop> =
        withContext(Dispatchers.IO) {
            val queryParam = JSONArray(query.map {
                JSONObject().apply {
                    put("label", it.label)
                    put("latitude", it.latitude)
                    put("longitude", it.longitude)
                }
            })

            val uri = Uri.Builder()
                .scheme("content")
                .authority(pluginAuthority)
                .path(SearchPluginContract.Paths.Search)
                .appendQueryParameter(SearchPluginContract.Paths.QueryParam, queryParam.toString())
                .appendQueryParameter(
                    SearchPluginContract.Paths.AllowNetworkParam,
                    allowNetwork.toString()
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

                val results = fromCursor(cursor, query) ?: emptyList()
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun fromCursor(cursor: Cursor, locations: List<Location>): List<PublicTransportStop>? {

        val idIdx =
            cursor.getColumnIndex(PublicTransportPluginContract.PublicTransportColumns.StationId)
                .takeIf { it != -1 } ?: return null
        val nameIdx =
            cursor.getColumnIndex(PublicTransportPluginContract.PublicTransportColumns.StationName)
                .takeIf { it != -1 }
        val providerIdx =
            cursor.getColumnIndex(PublicTransportPluginContract.PublicTransportColumns.Provider)
                .takeIf { it != -1 } ?: return null
        val latitudeIdx =
            cursor.getColumnIndex(PublicTransportPluginContract.PublicTransportColumns.Latitude)
                .takeIf { it != -1 }
        val longitudeIdx =
            cursor.getColumnIndex(PublicTransportPluginContract.PublicTransportColumns.Longitude)
                .takeIf { it != -1 }
        val lineIdx =
            cursor.getColumnIndex(PublicTransportPluginContract.PublicTransportColumns.Line)
                .takeIf { it != -1 } ?: return null
        val lineTypeIdx =
            cursor.getColumnIndex(PublicTransportPluginContract.PublicTransportColumns.LineType)
                .takeIf { it != -1 }
        val lastStopIdx =
            cursor.getColumnIndex(PublicTransportPluginContract.PublicTransportColumns.LastStop)
                .takeIf { it != -1 }
        val localTimeIdx =
            cursor.getColumnIndex(PublicTransportPluginContract.PublicTransportColumns.LocalTime)
                .takeIf { it != -1 } ?: return null

        if (nameIdx == null && (latitudeIdx == null || longitudeIdx == null)) {
            // need at least name or lat/lon for matching results
            return null
        }

        data class UniqueStopId(val id: String, val provider: String)

        val stops = mutableMapOf<UniqueStopId, PluginPublicTransportStop>()
        while (cursor.moveToNext()) {
            val id = cursor.getString(idIdx)
            val provider = cursor.getString(providerIdx)
            val lat = cursor.getDoubleOrNull(latitudeIdx ?: -1)
            val lon = cursor.getDoubleOrNull(longitudeIdx ?: -1)
            val name = cursor.getStringOrNull(nameIdx ?: -1)

            run {
                stops.getOrPut(UniqueStopId(id, provider)) {
                    PluginPublicTransportStop(
                        wrapLocation = locations.firstOrNull {
                            it.label == name || lat != null && lon != null && it.distanceTo(
                                lat,
                                lon
                            ) < 25f
                        } ?: return@run,
                        provider = provider
                    )
                }.mutableDepartures.add(
                    Departure(
                        time = LocalTime.parse(cursor.getString(localTimeIdx)),
                        line = cursor.getString(lineIdx),
                        lastStop = cursor.getStringOrNull(lastStopIdx ?: -1),
                        type = cursor.getStringOrNull(lineTypeIdx ?: -1)
                            ?.let { LineType.valueOf(it.uppercase()) }
                    )
                )
            }
        }

        return stops.values.takeIf { it.isNotEmpty() }?.toList()
    }
}