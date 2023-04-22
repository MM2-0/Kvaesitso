package de.mm20.launcher2.ui.launcher.search.common

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.geometry.Rect
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.appshortcuts.AppShortcutRepository
import de.mm20.launcher2.badges.BadgeService
import de.mm20.launcher2.files.FileRepository
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.LauncherApp
import de.mm20.launcher2.search.data.LauncherShortcut
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.services.tags.TagsService
import de.mm20.launcher2.ui.launcher.search.ListItemViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchableItemVM : ListItemViewModel(), KoinComponent {
    private val favoritesService: FavoritesService by inject()
    private val badgeService: BadgeService by inject()
    private val iconService: IconService by inject()
    private val tagsService: TagsService by inject()
    private val notificationRepository: NotificationRepository by inject()
    private val appShortcutRepository: AppShortcutRepository by inject()
    private val fileRepository: FileRepository by inject()

    private val searchable = MutableStateFlow<SavableSearchable?>(null)
    private val iconSize = MutableStateFlow<Int>(0)
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
        if (searchable !is LauncherApp) emptyFlow()
        else notificationRepository.notifications.map { it.filter { it.packageName == searchable.`package` } }
    }

    val shortcuts = searchable.map {
        if (it !is LauncherApp) emptyList()
        else appShortcutRepository.getShortcutsForActivity(it.launcherActivityInfo, 5)
    }

    open fun launch(context: Context, bounds: Rect? = null): Boolean {
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
        } else if (searchable is LauncherApp || searchable is AppShortcut) {
            favoritesService.reset(searchable)
        }
        return false
    }

    fun clearNotification(notification: StatusBarNotification) {
        notificationRepository.cancelNotification(notification)
    }

    fun getShortcutIcon(context: Context, shortcut: ShortcutInfo): Drawable? {
        val launcherApps = context.getSystemService<LauncherApps>() ?: return null
        return launcherApps.getShortcutIconDrawable(shortcut, 0)
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

    fun delete() {
        val searchable = searchable.value ?: return
        if (searchable is File) fileRepository.deleteFile(searchable)
        if (searchable is LauncherShortcut) appShortcutRepository.removePinnedShortcut(searchable)
        favoritesService.reset(searchable)
    }
}