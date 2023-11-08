package de.mm20.launcher2.openstreetmaps

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.getSystemService
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import java.util.concurrent.TimeUnit
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import retrofit2.Retrofit
import org.koin.core.component.inject
import retrofit2.HttpException
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.coroutines.cancellation.CancellationException

internal class OsmRepository(
    private val context: Context,
    private val dataStore: LauncherDataStore
) : SearchableRepository<de.mm20.launcher2.search.Location>, KoinComponent {

    private val permissionsManager: PermissionsManager by inject()
    private val hasLocationPermission = permissionsManager.hasPermission(PermissionGroup.Location)

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val httpClient = OkHttpClient
        .Builder()
        // TODO set sensible timeouts that dont cause issues
        //.connectTimeout(200, TimeUnit.MILLISECONDS)
        //.readTimeout(5000, TimeUnit.MILLISECONDS)
        //.writeTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    private lateinit var retrofit: Retrofit
    private lateinit var overpassService: OverpassApi

    init {
        scope.launch {
            try {
                retrofit = Retrofit.Builder()
                    .client(httpClient)
                    .baseUrl("https://overpass-api.de/") // TODO make configurable (maybe)
                    .addConverterFactory(OverpassQueryConverterFactory())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                overpassService = retrofit.create(OverpassApi::class.java)
            } catch (e: Exception) {
                CrashReporter.logException(e)
            }
        }
    }

    override fun search(query: String): Flow<ImmutableList<OsmLocation>> = channelFlow {
        send(persistentListOf())

        if (query.length < 3) return@channelFlow

        hasLocationPermission.collectLatest locationPermission@{ locationPermission ->
            if (!locationPermission) return@locationPermission

            withContext(Dispatchers.IO) {
                httpClient.dispatcher.cancelAll()
            }

            val userLocation = getCurrentLocation() ?: return@locationPermission

            dataStore.data.map { it.openStreetMapsSearch.searchRadius }
                .collectLatest dataStore@{ searchRadius ->
                    val result =
                        queryOverpass(query, searchRadius, userLocation.latitude, userLocation.longitude)

                    if (result != null)
                        send(OsmLocation.fromOverpassResponse(result, userLocation).toImmutableList())
                }

        }
    }

    private suspend fun queryOverpass(
        query: String,
        radius: Int,
        latitude: Double,
        longitude: Double
    ): OverpassResponse? {
        return try {
            overpassService.search(
                OverpassQuery(
                    name = query,
                    radius = radius,
                    latitude = latitude,
                    longitude = longitude,
                )
            )
        } catch (_: HttpException) {
            null
        } catch (_: CancellationException) {
            null
        } catch (e: Exception) {
            Log.e("OsmRepository", "Failed to search for $query", e)
            null
        }
    }

    private fun getCurrentLocation(): Location? {
        val lm = context.getSystemService<LocationManager>()!!
        val hasFineAccess = context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseAccess = context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

        return if (hasFineAccess) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        else if (hasCoarseAccess) lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        else null
    }
}