package de.mm20.launcher2.openstreetmaps

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.core.content.getSystemService
import com.balsikandar.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import java.util.concurrent.TimeUnit
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import retrofit2.Retrofit
import org.koin.core.component.inject

internal class OsmRepository(
    private val context: Context,
) : SearchableRepository<OsmLocation>, KoinComponent {

    private val permissionsManager: PermissionsManager by inject()
    private val hasLocationPermission = permissionsManager.hasPermission(PermissionGroup.Location)

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val httpClient = OkHttpClient
        .Builder()
        .connectTimeout(200, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .writeTimeout(1000, TimeUnit.MILLISECONDS)
        .build()

    private lateinit var retrofit: Retrofit
    private lateinit var overpassService: OverpassApi

    init {
        scope.launch {
            try {
                retrofit = Retrofit.Builder()
                    .client(httpClient)
                    .baseUrl("https://overpass-api.de/")
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

        hasLocationPermission.collectLatest {
            if (!it) return@collectLatest

            withContext(Dispatchers.IO) {
                httpClient.dispatcher.cancelAll()
            }

            val location = getCurrentLocation() ?: return@collectLatest

            val radius = 1000

            val encodedQuery = """[out:json];
                node(around:$radius,${location.latitude},${location.longitude})["name"~"$query",i];
                out;
            """.trimIndent()

            val response = overpassService.search(encodedQuery)
            if (!response.isSuccessful) return@collectLatest

            val jsonString = response.body?.string() ?: return@collectLatest


        }
    }

    private fun getCurrentLocation(): Location? {
        val lm = context.getSystemService<LocationManager>()!!
        var location: Location? = null
        if (context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        if (location == null && context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        return location
    }
}