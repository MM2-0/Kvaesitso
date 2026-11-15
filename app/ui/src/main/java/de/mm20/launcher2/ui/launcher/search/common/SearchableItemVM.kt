package de.mm20.launcher2.ui.launcher.search.common

import android.content.Context
import android.os.UserHandle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.unit.IntRect
import androidx.core.app.ActivityOptionsCompat
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.appshortcuts.AppShortcutRepository
import de.mm20.launcher2.badges.BadgeService
import de.mm20.launcher2.devicepose.DevicePoseProvider
import de.mm20.launcher2.freeze.FreezeManager
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.notifications.Notification
import de.mm20.launcher2.notifications.NotificationRepository
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.search.ContactSearchSettings
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import de.mm20.launcher2.sandbox.AppCloner
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.Folder
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.UpdatableSearchable
import de.mm20.launcher2.search.UpdateResult
import de.mm20.launcher2.search.WebAppShortcut
import de.mm20.launcher2.services.favorites.FavoritesService
import de.mm20.launcher2.services.tags.TagsService
import de.mm20.launcher2.ui.launcher.search.ListItemViewModel
import de.mm20.launcher2.ui.settings.protection.authenticateSettings
import de.mm20.launcher2.ui.settings.protection.canAuthenticateSettings
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalCoroutinesApi::class)
class SearchableItemVM : ListItemViewModel(), KoinComponent {
    private val favoritesService: FavoritesService by inject()
    private val badgeService: BadgeService by inject()
    private val iconService: IconService by inject()
    private val tagsService: TagsService by inject()
    private val notificationRepository: NotificationRepository by inject()
    private val appRepository: AppRepository by inject()
    private val appShortcutRepository: AppShortcutRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val locationSearchSettings: LocationSearchSettings by inject()
    private val contactSearchSettings: ContactSearchSettings by inject()
    private val freezeManager: FreezeManager by inject()
    private val freezeSettings: de.mm20.launcher2.preferences.freeze.FreezeSettings by inject()
    private val appLockManager: de.mm20.launcher2.applock.AppLockManager by inject()
    private val appLockSettings: de.mm20.launcher2.preferences.applock.AppLockSettings by inject()
    private val searchableRepository: de.mm20.launcher2.searchable.SavableSearchableRepository by
        inject()

    val isUpToDate = MutableStateFlow(true)

    val devicePoseProvider: DevicePoseProvider by inject()
    val frozenAppStyle = freezeSettings.frozenAppStyle

    val searchable = MutableStateFlow<SavableSearchable?>(null)
    private val iconSize = MutableStateFlow(0)

    fun init(searchable: SavableSearchable, iconSize: Int) {
        this.searchable.value = searchable
        this.iconSize.value = iconSize
    }

    val advancedFeaturesEnabled =
        freezeSettings.advancedFeaturesEnabled.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )

    val isPinned = searchable.flatMapLatest {
        if (it == null) emptyFlow() else favoritesService.isPinned(it)
    }

    fun pin() {
        searchable.value?.let { favoritesService.pinItem(it) }
    }

    fun unpin() {
        searchable.value?.let { favoritesService.unpinItem(it) }
    }

    val badge =
        searchable
            .flatMapLatest {
                if (it == null) emptyFlow() else badgeService.getBadge(it)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dockBadge =
        searchable
            .flatMapLatest {
                if (it == null) emptyFlow() else badgeService.getBadge(it, includeFrozen = false)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val icon =
        searchable
            .combine(iconSize) { sh, sz -> sh to sz }
            .flatMapLatest { (s, size) ->
                if (s == null || size == 0) emptyFlow() else iconService.getIcon(s, size)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tags = searchable.flatMapLatest {
        if (it == null) emptyFlow() else tagsService.getTags(it)
    }

    val notifications = searchable.flatMapLatest { searchable ->
        if (searchable !is Application) emptyFlow()
        else
            notificationRepository.notifications.map {
                it.filter {
                    it.packageName == searchable.componentName.packageName && !it.isGroupSummary
                }
            }
    }

    /**
     * Live suspended (frozen) state of this app. Backed by the app repository, which reacts to the
     * system's package-suspended callbacks, so this updates immediately on freeze/unfreeze.
     */
    val isSuspended =
        searchable
            .flatMapLatest { searchable ->
                if (searchable !is Application) flowOf(false)
                else
                    appRepository
                        .findOne(searchable.componentName.packageName, searchable.user)
                        // Also covers apps frozen by disabling them (not just FLAG_SUSPENDED), so
                        // the toolbar
                        // offers "Unfreeze" for those as well.
                        .map {
                            it?.isSuspended == true ||
                                (searchable.user == android.os.Process.myUserHandle() &&
                                    freezeManager.isFrozen(searchable.componentName.packageName))
                        }
                        .distinctUntilChanged()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val children = searchable.flatMapLatest {
        when (it) {
            is Application ->
                appShortcutRepository.findMany(
                    componentName = it.componentName,
                    user = it.user,
                    manifest = true,
                    dynamic = true,
                )

            is AppShortcut -> {
                val packageName =
                    it.componentName?.packageName ?: return@flatMapLatest flowOf(emptyList())
                appRepository
                    .findOne(
                        packageName = packageName,
                        user = it.user,
                    )
                    .map { listOfNotNull(it) }
            }

            is Folder -> searchableRepository.getByKeys(it.itemKeys)

            else -> flowOf(emptyList())
        }
    }

    fun launch(context: Context, bounds: IntRect? = null): Boolean {
        return launchTarget(context, searchable.value ?: return false, bounds)
    }

    /** Shared entry for icons, shutters and folder children, including app lock and thawing. */
    fun launchTarget(
        context: Context,
        searchable: SavableSearchable,
        bounds: IntRect? = null,
    ): Boolean {
        val activity = context as? AppCompatActivity
        val promptTitle = context.getString(I18nR.string.app_lock_prompt_title, searchable.label)

        if (searchable is Application) {
            val packageName = searchable.componentName.packageName
            viewModelScope.launch {
                if (appLockManager.isLocked(packageName)) {
                    val method = appLockSettings.lockMethod.first()
                    if (activity != null && canAuthenticateSettings(activity, method)) {
                        authenticateSettings(activity, method, promptTitle) { success ->
                            if (success) {
                                appLockManager.reportUnlocked(packageName)
                                launchDirect(context, searchable, bounds)
                            }
                        }
                    }
                } else {
                    launchDirect(context, searchable, bounds)
                }
            }
            return true
        }

        if (searchable is WebAppShortcut) {
            viewModelScope.launch {
                if (appLockManager.isLocked(searchable.key)) {
                    val method = appLockSettings.lockMethod.first()
                    if (activity != null && canAuthenticateSettings(activity, method)) {
                        authenticateSettings(activity, method, promptTitle) { success ->
                            if (success) {
                                appLockManager.reportUnlocked(searchable.key)
                                launchDirect(context, searchable, bounds)
                            }
                        }
                    }
                } else {
                    launchDirect(context, searchable, bounds)
                }
            }
            return true
        }

        return launchDirect(context, searchable, bounds)
    }

    private fun launchDirect(
        context: Context,
        searchable: SavableSearchable,
        bounds: IntRect?,
    ): Boolean {
        val view = (context as? AppCompatActivity)?.window?.decorView
        val options =
            if (bounds != null && view != null) {
                ActivityOptionsCompat.makeScaleUpAnimation(
                    view,
                    bounds.left,
                    bounds.top,
                    bounds.width,
                    bounds.height,
                )
            } else {
                ActivityOptionsCompat.makeBasic()
            }
        val bundle = options.toBundle()

        if (
            searchable is Application &&
                searchable.getSerializer() !is de.mm20.launcher2.search.VirtualAppSerializer
        ) {
            viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
                val pkg = searchable.componentName.packageName
                try {
                    // Read persisted rules for this launch. A WhileSubscribed StateFlow without
                    // collectors stays at its initial empty value and silently skips companions.
                    val needsThaw =
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            searchable.isSuspended ||
                                (searchable.user == android.os.Process.myUserHandle() &&
                                    (freezeManager.isFrozen(pkg) ||
                                        pkg in freezeSettings.companionEnabled.first()))
                        }
                    if (!needsThaw) {
                        if (searchable.launch(context, bundle)) reportUsage(searchable)
                        else favoritesService.reset(searchable)
                        return@launch
                    }
                    freezeManager.unfreezeCompanions(pkg, searchable.user.hashCode())
                    val started =
                        freezeManager.launchThawed(
                            pkg,
                            searchable.user,
                            searchable.componentName,
                        ) { component ->
                            try {
                                requireNotNull(
                                        context.getSystemService(
                                            android.content.pm.LauncherApps::class.java
                                        )
                                    )
                                    .startMainActivity(component, searchable.user, null, bundle)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }
                    if (!started) freezeManager.releaseCompanions(pkg, searchable.user.hashCode())
                    if (started) reportUsage(searchable)
                    else
                        android.widget.Toast.makeText(
                                context,
                                I18nR.string.pdft_error_title,
                                android.widget.Toast.LENGTH_LONG,
                            )
                            .show()
                } catch (error: Exception) {
                    freezeManager.releaseCompanions(pkg, searchable.user.hashCode())
                    android.util.Log.e("AppLaunch", "Could not launch frozen app", error)
                    android.widget.Toast.makeText(
                            context,
                            I18nR.string.pdft_error_title,
                            android.widget.Toast.LENGTH_LONG,
                        )
                        .show()
                }
            }
            return true
        }

        if (searchable.launch(context, bundle)) {
            reportUsage(searchable)
            return true
        } else if (searchable is Application || searchable is AppShortcut) {
            favoritesService.reset(searchable)
        }
        return false
    }

    fun clearNotification(notification: Notification) {
        notificationRepository.cancelNotification(notification)
    }

    fun getChildIcon(child: SavableSearchable, size: Int): Flow<LauncherIcon?> {
        return iconService.getIcon(child, size)
    }

    fun isChildPinned(child: SavableSearchable): Flow<Boolean> {
        return favoritesService.isPinned(child)
    }

    fun pinChild(child: SavableSearchable) {
        favoritesService.pinItem(child)
    }

    fun unpinChild(child: SavableSearchable) {
        favoritesService.unpinItem(child)
    }

    fun launchChild(context: Context, child: SavableSearchable) {
        launchTarget(context, child)
    }

    fun launchChildAt(context: Context, index: Int) {
        viewModelScope.launch {
            val child = children.first().getOrNull(index)
            if (child != null) {
                launchChild(context, child)
            }
        }
    }

    /**
     * Freezes this app right away and also marks it as an auto-freeze candidate, so the auto-freeze
     * triggers (screen off, idle, battery saver - whichever the user has enabled) keep freezing it
     * going forward instead of this being a one-off action.
     */
    // === TELOS_PENDING_REVIEW_START: multi_user_freeze ===
    fun freeze() {
        val searchable = searchable.value
        if (searchable is Application) {
            val packageName = searchable.componentName.packageName
            val userId = searchable.user.hashCode()
            viewModelScope.launch {
                freezeManager.freeze(packageName, userId)
            }
            freezeManager.setAutoFreezeCandidate(packageName, true)
        }
    }

    fun unfreeze() {
        val searchable = searchable.value
        if (searchable is Application) {
            val packageName = searchable.componentName.packageName
            val userId = searchable.user.hashCode()
            viewModelScope.launch {
                freezeManager.unfreeze(packageName, userId)
            }
        }
    }

    fun forceStop() {
        val searchable = searchable.value
        if (searchable is Application) {
            val packageName = searchable.componentName.packageName
            val userId = searchable.user.hashCode()
            viewModelScope.launch {
                freezeManager.forceStop(packageName, userId)
            }
        }
    }

    fun clearCache() {
        val searchable = searchable.value
        if (searchable is Application) {
            val packageName = searchable.componentName.packageName
            val userId = searchable.user.hashCode()
            viewModelScope.launch {
                freezeManager.clearCache(packageName, userId)
            }
        }
    }

    // === TELOS_PENDING_REVIEW_END: multi_user_freeze ===

    // === TELOS_PENDING_REVIEW_START: sandbox_cloning_and_bridge ===
    fun cloneToSandbox(context: Context, workProfileUser: UserHandle) {
        val searchable = searchable.value
        if (searchable is Application) {
            viewModelScope.launch {
                val success = AppCloner.cloneAppToWorkProfile(context, searchable, workProfileUser)
                if (success) {
                    Log.d("SmartFreeze", "Cloned successfully")
                } else {
                    Log.e("SmartFreeze", "Cloning failed")
                }
            }
        }
    }

    // === TELOS_PENDING_REVIEW_END: sandbox_cloning_and_bridge ===

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

    private var shouldRetryUpdate = false

    fun requestUpdatedSearchable(context: Context) {
        val searchable = searchable.value ?: return
        if (searchable is UpdatableSearchable<*>) {
            val updatedSelf = searchable.updatedSelf ?: return
            val sinceTimestamp = System.currentTimeMillis() - searchable.timestamp

            val isOutOfDate = 1.minutes.inWholeMilliseconds < sinceTimestamp

            if (!shouldRetryUpdate && !isOutOfDate) return

            viewModelScope.launch {
                with(updatedSelf(searchable)) {
                    when (this) {
                        is UpdateResult.Success -> {
                            isUpToDate.value = true
                            shouldRetryUpdate = false
                            favoritesService.upsert(this.result)
                        }

                        is UpdateResult.TemporarilyUnavailable -> {
                            isUpToDate.value = false
                            shouldRetryUpdate = true
                        }

                        is UpdateResult.PermanentlyUnavailable -> {
                            isUpToDate.value = false
                            shouldRetryUpdate = false
                            favoritesService.delete(searchable)
                            Toast.makeText(
                                    context,
                                    I18nR.string.unavailable_searchable,
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                            Log.d("requestUpdatedSearchable", "PermanentlyUnavailable", this.cause)
                        }
                    }
                }
            }
        }
    }

    fun requestShortcutPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.AppShortcuts)
    }

    val measurementSystem =
        locationSearchSettings.measurementSystem.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            MeasurementSystem.Metric,
        )

    val showMap =
        locationSearchSettings.showMap.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )

    val applyMapTheming =
        locationSearchSettings.themeMap.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )

    val mapTileServerUrl =
        locationSearchSettings.tileServer
            .map { it ?: LocationSearchSettings.DefaultTileServerUrl }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val callOnTap =
        contactSearchSettings.callOnTap.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )

    fun reportUsage(searchable: SavableSearchable) {
        favoritesService.reportLaunch(searchable)
    }
}
