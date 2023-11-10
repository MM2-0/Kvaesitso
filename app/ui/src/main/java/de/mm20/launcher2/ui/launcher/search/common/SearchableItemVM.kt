package de.mm20.launcher2.ui.launcher.search.common

import android.Manifest
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.geometry.Rect
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.appshortcuts.AppShortcutRepository
import de.mm20.launcher2.badges.BadgeService
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.checkPermission
import de.mm20.launcher2.notifications.Notification
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.services.tags.TagsService
import de.mm20.launcher2.ui.launcher.search.ListItemViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchableItemVM : ListItemViewModel(), KoinComponent {
    private val favoritesService: FavoritesService by inject()
    private val badgeService: BadgeService by inject()
    private val iconService: IconService by inject()
    private val tagsService: TagsService by inject()
    private val notificationRepository: NotificationRepository by inject()
    private val appShortcutRepository: AppShortcutRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val dataStore: LauncherDataStore by inject()

    private val searchable = MutableStateFlow<SavableSearchable?>(null)
    private val iconSize = MutableStateFlow(0)
    fun init(searchable: SavableSearchable, iconSize: Int) {
        this.searchable.value = searchable
        this.iconSize.value = iconSize
    }

    val isPinned = searchable.flatMapLatest {
        if (it == null) emptyFlow() else favoritesService.isPinned(it)
    }

    fun pin() {
        searchable.value?.let { favoritesService.pinItem(it) }
    }

    fun unpin() {
        searchable.value?.let { favoritesService.unpinItem(it) }
    }

    val isHidden = searchable.flatMapLatest {
        if (it == null) emptyFlow() else favoritesService.isHidden(it)
    }

    fun hide() {
        searchable.value?.let { favoritesService.hideItem(it) }
    }

    fun unhide() {
        searchable.value?.let { favoritesService.unhideItem(it) }
    }

    val badge = searchable.flatMapLatest {
        if (it == null) emptyFlow() else badgeService.getBadge(it)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val icon = searchable.combine(iconSize) { sh, sz -> sh to sz }.flatMapLatest { (s, size) ->
        if (s == null || size == 0) emptyFlow() else iconService.getIcon(s, size)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val tags = searchable.flatMapLatest {
        if (it == null) emptyFlow() else tagsService.getTags(it)
    }

    val notifications = searchable.flatMapLatest { searchable ->
        if (searchable !is Application) emptyFlow()
        else notificationRepository.notifications.map { it.filter { it.packageName == searchable.componentName.packageName && !it.isGroupSummary } }
    }

    val shortcuts = searchable.map {
        if (it !is Application) emptyList()
        else appShortcutRepository
            .findMany(
                componentName = it.componentName,
                user = it.user,
                manifest = true,
                dynamic = true,
            ).first()
    }

    fun launch(context: Context, bounds: Rect? = null): Boolean {
        val searchable = searchable.value ?: return false
        val view = (context as? AppCompatActivity)?.window?.decorView
        val options = if (bounds != null && view != null) {
            ActivityOptionsCompat.makeScaleUpAnimation(
                view,
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.width.toInt(),
                bounds.height.toInt()
            )
        } else {
            ActivityOptionsCompat.makeBasic()
        }
        val bundle = options.toBundle()
        if (searchable.launch(context, bundle)) {
            favoritesService.reportLaunch(searchable)
            return true
        } else if (searchable is Application || searchable is AppShortcut) {
            favoritesService.reset(searchable)
        }
        return false
    }

    fun clearNotification(notification: Notification) {
        notificationRepository.cancelNotification(notification)
    }

    fun getShortcutIcon(context: Context, shortcut: AppShortcut, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(shortcut, size)
    }

    fun isShortcutPinned(shortcut: AppShortcut): Flow<Boolean> {
        return favoritesService.isPinned(shortcut)
    }

    fun pinShortcut(shortcut: AppShortcut) {
        favoritesService.pinItem(shortcut)
    }

    fun unpinShortcut(shortcut: AppShortcut) {
        favoritesService.unpinItem(shortcut)
    }

    fun launchShortcut(context: Context, shortcut: AppShortcut) {
        shortcut.launch(context, null)
    }

    fun delete(context: Context) {
        val searchable = searchable.value ?: return
        if (searchable is File) {
            viewModelScope.launch {
                searchable.delete(context.applicationContext)
            }
        }
        if (searchable is AppShortcut) {
            viewModelScope.launch {
                searchable.delete(context.applicationContext)
            }
        }
        favoritesService.reset(searchable)
    }

    fun requestShortcutPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.AppShortcuts)
    }

    val useInsaneUnits = dataStore.data.map { it.locationsSearch.useInsaneUnits }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private var declination: Float? = null
    private fun updateDeclination(location: Location) {
        declination = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            location.time
        ).declination
    }

    fun getUserLocation(context: Context): Flow<Location> = callbackFlow {
        val locationCallback = LocationListener {
            updateDeclination(it)
            trySend(it)
        }

        context
            .getSystemService<LocationManager>()
            ?.runCatching {
                val hasFineAccess =
                    context.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                val hasCoarseAccess =
                    context.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

                val location =
                    (if (hasFineAccess) this.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null)
                        ?: if (hasCoarseAccess) this.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null

                if (location != null) {
                    updateDeclination(location)
                    trySend(location)
                }

                if (hasFineAccess) {
                    this.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000,
                        1f,
                        locationCallback
                    )
                }
                if (hasCoarseAccess) {
                    this.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000,
                        1f,
                        locationCallback
                    )
                }
            }?.onFailure {
                Log.e("SearchableItemVM", "Failed to start location updates", it)
            }

        awaitClose {
            context.getSystemService<LocationManager>()?.removeUpdates(locationCallback)
        }
    }

    fun getUserHeading(context: Context): Flow<Float> = callbackFlow {
        val sensorCallback = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR)
                    return

                val rotationMatrix = FloatArray(9)
                val orientationAngles = FloatArray(3)

                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                trySend(
                    // eastward heading from magnetic north plus correction for geographic north, if available
                    orientationAngles[0] * 180f / Math.PI.toFloat() + (declination ?: 0f)
                )
            }
        }

        context
            .getSystemService<SensorManager>()
            ?.runCatching {
                this.registerListener(
                    sensorCallback,
                    this.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return@runCatching,
                    SensorManager.SENSOR_DELAY_UI
                )
            }?.onFailure {
                Log.e("SearchableItemVM", "Failed to start heading updates", it)
            }

        awaitClose {
            context.getSystemService<SensorManager>()?.unregisterListener(sensorCallback)
        }
    }
}