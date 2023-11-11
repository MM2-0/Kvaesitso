package de.mm20.launcher2.openstreetmaps

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
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

internal open class BaseOsmRepository(
    overpassUrl: String
) {
    protected val httpClient = OkHttpClient()
    protected lateinit var overpassService: OverpassApi

    init {
        CoroutineScope(Job() + Dispatchers.Default).launch {
            try {
                setBaseUrl(overpassUrl)
            } catch (e: Exception) {
                Log.e("OsmRepository", "Failed to create overpassService", e)
            }
        }
    }

    private fun setBaseUrl(url: String) {
        overpassService = Retrofit.Builder()
            .client(httpClient)
            .baseUrl(url)
            .addConverterFactory(OverpassQueryConverterFactory())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OverpassApi::class.java)
    }

    suspend fun searchForId(id: Long): OsmLocation? = try {
        overpassService.search(
            OverpassIdQuery(
                id = id
            )
        )
    } catch (_: HttpException) {
        null
    } catch (_: CancellationException) {
        null
    } catch (e: Exception) {
        Log.e("OsmRepository", "Failed to search for $id", e)
        null
    }?.let {
        OsmLocation.fromOverpassResponse(it)
    }?.firstOrNull()
}

internal class OsmRepository(
    private val context: Context,
    private val dataStore: LauncherDataStore
) : BaseOsmRepository("https://overpass-api.de/"),
    SearchableRepository<OsmLocation>,
    KoinComponent {

    private val permissionsManager: PermissionsManager by inject()
    private val hasLocationPermission = permissionsManager.hasPermission(PermissionGroup.Location)

    override fun search(query: String): Flow<ImmutableList<OsmLocation>> = channelFlow {
        send(persistentListOf())

        if (query.length < 3) return@channelFlow

        hasLocationPermission.collectLatest locationPermission@{ locationPermission ->
            if (!locationPermission) return@locationPermission

            withContext(Dispatchers.IO) {
                httpClient.dispatcher.cancelAll()
            }

            val userLocation = getCurrentLocation() ?: return@locationPermission

            dataStore.data.map { it.locationsSearch.searchRadius }
                .collectLatest dataStore@{ searchRadius ->
                    val result = try {
                        overpassService.search(
                            OverpassFuzzyRadiusQuery(
                                query = query,
                                radius = searchRadius,
                                latitude = userLocation.latitude,
                                longitude = userLocation.longitude,
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

                    if (result != null)
                        send(OsmLocation.fromOverpassResponse(result).toImmutableList())
                }

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