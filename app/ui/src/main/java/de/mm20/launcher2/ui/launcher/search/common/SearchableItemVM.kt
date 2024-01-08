package de.mm20.launcher2.ui.launcher.search.common

import android.content.Context
import android.os.Debug
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Rect
import androidx.core.app.ActivityOptionsCompat
import de.mm20.launcher2.appshortcuts.AppShortcutRepository
import de.mm20.launcher2.badges.BadgeService
import de.mm20.launcher2.devicepose.DevicePoseProvider
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.notifications.Notification
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.openstreetmaps.settings.LocationSearchSettings
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.DeferredSearchable
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.services.tags.TagsService
import de.mm20.launcher2.ui.launcher.search.ListItemViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    private val locationSearchSettings: LocationSearchSettings by inject()

    val isUpToDate = MutableStateFlow(true)

    val devicePoseProvider: DevicePoseProvider by inject()

    val searchable = MutableStateFlow<SavableSearchable?>(null)
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

    fun requestUpdatedDeferredSearchable() {
        val searchable = searchable.value ?: return
        if (searchable is DeferredSearchable<*>) {
            val updated = searchable.updatedSelf ?: return
            viewModelScope.launch {
                this@SearchableItemVM.searchable.value = with (updated.await()) {
                    when (this) {
                        is UpdateResult.Success -> {
                            isUpToDate.value = true
                            favoritesService.upsert(this.result)
                            this.result
                        }
                        is UpdateResult.TemporarilyUnavailable -> {
                            isUpToDate.value = false
                            return@launch
                        }
                        is UpdateResult.PermanentlyUnavailable -> {
                            isUpToDate.value = false
                            favoritesService.delete(searchable)
                            // notifyUserOfInvalidation()
                            null
                        }
                    }
                }
            }
        }
    }

    fun requestShortcutPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.AppShortcuts)
    }

    val useInsaneUnits = locationSearchSettings.imperialUnits
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val showMap = locationSearchSettings.showMap
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val applyMapTheming = locationSearchSettings.themeMap
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val showPositionOnMap = locationSearchSettings.showPositionOnMap
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val mapTileServerUrl = locationSearchSettings.tileServer
        .stateIn(viewModelScope, SharingStarted.Lazily, "")
}