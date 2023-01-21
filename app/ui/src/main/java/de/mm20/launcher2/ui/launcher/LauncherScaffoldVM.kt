package de.mm20.launcher2.ui.launcher

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.globalactions.GlobalActionsService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.GestureSettings
import de.mm20.launcher2.preferences.Settings.GestureSettings.GestureAction
import de.mm20.launcher2.ui.gestures.Gesture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LauncherScaffoldVM : ViewModel(), KoinComponent {

    private val dataStore: LauncherDataStore by inject()
    private val globalActionsService: GlobalActionsService by inject()
    private val permissionsManager: PermissionsManager by inject()

    private var gestureSettings : GestureSettings? = null


    init {
        viewModelScope.launch {
            dataStore.data.map { it.gestures }.collectLatest {
                gestureSettings = it
            }
        }
    }

    private var isSystemInDarkMode = MutableStateFlow(false)

    private val dimBackgroundState = combine(
        dataStore.data.map { it.appearance.dimWallpaper },
        dataStore.data.map { it.appearance.theme },
        isSystemInDarkMode
    ) { dim, theme, systemDarkMode ->
        dim && (theme == Settings.AppearanceSettings.Theme.Dark || theme == Settings.AppearanceSettings.Theme.System && systemDarkMode)
    }
    val dimBackground = dimBackgroundState.asLiveData()

    val statusBarColor = dataStore.data.map { it.systemBars.statusBarColor }.asLiveData()
    val navBarColor = dataStore.data.map { it.systemBars.statusBarColor }.asLiveData()

    val hideNavBar = dataStore.data.map { it.systemBars.hideNavBar }.asLiveData()
    val hideStatusBar = dataStore.data.map { it.systemBars.hideStatusBar }.asLiveData()

    fun setSystemInDarkMode(darkMode: Boolean) {
        isSystemInDarkMode.value = darkMode
    }

    val baseLayout = dataStore.data.map { it.layout.baseLayout }.asLiveData()
    val bottomSearchBar = dataStore.data.map { it.layout.bottomSearchBar }.asLiveData()
    val reverseSearchResults = dataStore.data.map { it.layout.reverseSearchResults }.asLiveData()
    val fixedSearchBar = dataStore.data.map { it.layout.fixedSearchBar }.asLiveData()

    val isSearchOpen = MutableLiveData(false)
    val isWidgetEditMode = MutableLiveData(false)

    val searchBarFocused = MutableLiveData(false)


    val autoFocusSearch = dataStore.data.map { it.searchBar.autoFocus }

    fun setSearchbarFocus(focused: Boolean) {
        if (searchBarFocused.value != focused) searchBarFocused.value = focused
    }

    fun openSearch() {
        if (isSearchOpen.value == true) return
        isSearchOpen.value = true
        viewModelScope.launch {
            if (autoFocusSearch.first()) setSearchbarFocus(true)
        }
    }

    fun closeSearch() {
        if (isSearchOpen.value == false) return
        isSearchOpen.value = false
        setSearchbarFocus(false)
    }

    fun toggleSearch() {
        if (isSearchOpen.value == true) closeSearch()
        else openSearch()
    }

    fun setWidgetEditMode(editMode: Boolean) {
        isSearchOpen.value = false
        isWidgetEditMode.value = editMode
    }

    val wallpaperBlur = dataStore.data.map { it.appearance.blurWallpaper }.asLiveData()

    val fillClockHeight = dataStore.data.map { it.clockWidget.fillHeight }.asLiveData()
    val searchBarColor = dataStore.data.map { it.searchBar.color }.asLiveData()
    val searchBarStyle = dataStore.data.map { it.searchBar.searchBarStyle }.asLiveData()


    var failedGestureState by mutableStateOf<FailedGesture?>(null)
    fun handleGesture(gesture: Gesture): Boolean {
        val action = when (gesture) {
            Gesture.DoubleTap -> gestureSettings?.doubleTap
            Gesture.LongPress -> gestureSettings?.longPress
            Gesture.SwipeDown -> gestureSettings?.swipeDown?.takeIf { baseLayout.value != Settings.LayoutSettings.Layout.PullDown }
            Gesture.SwipeLeft -> gestureSettings?.swipeLeft?.takeIf { baseLayout.value != Settings.LayoutSettings.Layout.Pager }
            Gesture.SwipeRight -> gestureSettings?.swipeRight?.takeIf { baseLayout.value != Settings.LayoutSettings.Layout.PagerReversed }
        }
        val requiresAccessibilityService =
            action == GestureAction.OpenRecents
                    || action == GestureAction.OpenPowerDialog
                    || action == GestureAction.OpenQuickSettings
                    || action == GestureAction.OpenNotificationDrawer
                    || action == GestureAction.LockScreen

        if (action != null && requiresAccessibilityService && !permissionsManager.checkPermissionOnce(PermissionGroup.Accessibility)) {
            failedGestureState = FailedGesture(gesture, action)
            return true
        }


        return when (action) {
            GestureAction.OpenSearch -> {
                openSearch()
                true
            }

            GestureAction.OpenNotificationDrawer -> {
                globalActionsService.openNotificationDrawer()
                true
            }

            GestureAction.OpenQuickSettings -> {
                globalActionsService.openQuickSettings()
                true
            }

            GestureAction.LockScreen -> {
                globalActionsService.lockScreen()
                true
            }

            GestureAction.OpenPowerDialog -> {
                globalActionsService.openPowerDialog()
                true
            }

            GestureAction.OpenRecents -> {
                globalActionsService.openRecents()
                true
            }

            else -> false
        }
    }

    fun dismissGestureFailedSheet() {
        failedGestureState = null
    }
}

data class FailedGesture(val gesture: Gesture, val action: GestureAction)