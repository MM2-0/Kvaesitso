package de.mm20.launcher2.ui.launcher

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.searchable.SearchableRepository
import de.mm20.launcher2.globalactions.GlobalActionsService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.GestureSettings.GestureAction
import de.mm20.launcher2.preferences.Settings.LayoutSettings.Layout
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.gestures.Gesture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LauncherScaffoldVM : ViewModel(), KoinComponent {

    private val dataStore: LauncherDataStore by inject()
    private val globalActionsService: GlobalActionsService by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val searchableRepository: SearchableRepository by inject()

    private var isSystemInDarkMode = MutableStateFlow(false)

    private val dimBackgroundState = combine(
        dataStore.data.map { it.appearance.dimWallpaper },
        dataStore.data.map { it.appearance.theme },
        isSystemInDarkMode
    ) { dim, theme, systemDarkMode ->
        dim && (theme == Settings.AppearanceSettings.Theme.Dark || theme == Settings.AppearanceSettings.Theme.System && systemDarkMode)
    }
    val dimBackground = dimBackgroundState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val statusBarColor = dataStore.data.map { it.systemBars.statusBarColor }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val navBarColor = dataStore.data.map { it.systemBars.statusBarColor }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val chargingAnimation = dataStore.data.map { it.animations.charging }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val hideNavBar = dataStore.data.map { it.systemBars.hideNavBar }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val hideStatusBar = dataStore.data.map { it.systemBars.hideStatusBar }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setSystemInDarkMode(darkMode: Boolean) {
        isSystemInDarkMode.value = darkMode
    }

    val baseLayout = dataStore.data.map { it.layout.baseLayout }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val bottomSearchBar = dataStore.data.map { it.layout.bottomSearchBar }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val reverseSearchResults = dataStore.data.map { it.layout.reverseSearchResults }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val fixedSearchBar = dataStore.data.map { it.layout.fixedSearchBar }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val fixedRotation = dataStore.data.map { it.layout.fixedRotation }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val isSearchOpen = mutableStateOf(false)
    val isWidgetEditMode = mutableStateOf(false)

    val searchBarFocused = mutableStateOf(false)


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
        if (!isSearchOpen.value) return
        isSearchOpen.value = false
        setSearchbarFocus(false)
    }

    var skipNextSearchAnimation = false
    fun closeSearchWithoutAnimation() {
        if (!isSearchOpen.value) return
        skipNextSearchAnimation = true
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

    val wallpaperBlur = dataStore.data.map { it.appearance.blurWallpaper }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val fillClockHeight = dataStore.data.map { it.clockWidget.fillHeight }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val searchBarColor = dataStore.data.map { it.searchBar.color }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), Settings.SearchBarSettings.SearchBarColors.Auto)
    val searchBarStyle = dataStore.data.map { it.searchBar.searchBarStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), Settings.SearchBarSettings.SearchBarStyle.Transparent)

    val gestureState: StateFlow<GestureState> = dataStore
        .data.map { it.gestures }
        .distinctUntilChanged()
        .combine(baseLayout) { settings, layout ->
            val swipeLeftAction =
                settings?.swipeLeft?.takeIf { layout != Layout.Pager } ?: GestureAction.None
            val swipeRightAction = settings?.swipeRight?.takeIf { layout != Layout.PagerReversed }
                ?: GestureAction.None
            val swipeDownAction =
                settings?.swipeDown?.takeIf { layout != Layout.PullDown } ?: GestureAction.None
            val longPressAction = settings?.longPress ?: GestureAction.None
            val doubleTapAction = settings?.doubleTap ?: GestureAction.None
            val homeButtonAction = settings?.homeButton ?: GestureAction.None

            val swipeLeftAppKey =
                if (swipeLeftAction == GestureAction.LaunchApp) settings.swipeLeftApp else null
            val swipeRightAppKey =
                if (swipeRightAction == GestureAction.LaunchApp) settings.swipeRightApp else null
            val swipeDownAppKey =
                if (swipeDownAction == GestureAction.LaunchApp) settings.swipeDownApp else null
            val longPressAppKey =
                if (longPressAction == GestureAction.LaunchApp) settings.longPressApp else null
            val doubleTapAppKey =
                if (doubleTapAction == GestureAction.LaunchApp) settings.doubleTapApp else null
            val homeButtonAppKey =
                if (homeButtonAction == GestureAction.LaunchApp) settings.homeButtonApp else null
            val apps = listOfNotNull(
                swipeLeftAppKey,
                swipeRightAppKey,
                swipeDownAppKey,
                longPressAppKey,
                doubleTapAppKey,
                homeButtonAppKey,
            ).let { searchableRepository.getByKeys(it) }

            GestureState(
                swipeLeftAction = swipeLeftAction,
                swipeRightAction = swipeRightAction,
                swipeDownAction = swipeDownAction,
                longPressAction = longPressAction,
                doubleTapAction = doubleTapAction,
                homeButtonAction = homeButtonAction,
                swipeLeftApp = apps.firstOrNull { it.key == swipeLeftAppKey },
                swipeRightApp = apps.firstOrNull { it.key == swipeRightAppKey },
                swipeDownApp = apps.firstOrNull { it.key == swipeDownAppKey },
                longPressApp = apps.firstOrNull { it.key == longPressAppKey },
                doubleTapApp = apps.firstOrNull { it.key == doubleTapAppKey },
                homeButtonApp = apps.firstOrNull { it.key == homeButtonAppKey },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, GestureState())

    var failedGestureState by mutableStateOf<FailedGesture?>(null)
    fun handleGesture(context: Context, gesture: Gesture): Boolean {
        val action = when (gesture) {
            Gesture.DoubleTap -> gestureState.value.doubleTapAction
            Gesture.LongPress -> gestureState.value.longPressAction
            Gesture.SwipeDown -> gestureState.value.swipeDownAction.takeIf { baseLayout.value != Layout.PullDown }
            Gesture.SwipeLeft -> gestureState.value.swipeLeftAction.takeIf { baseLayout.value != Layout.Pager }
            Gesture.SwipeRight -> gestureState.value.swipeRightAction.takeIf { baseLayout.value != Layout.PagerReversed }
            Gesture.HomeButton -> gestureState.value.homeButtonAction
        }
        val requiresAccessibilityService =
            action == GestureAction.OpenRecents
                    || action == GestureAction.OpenPowerDialog
                    || action == GestureAction.OpenQuickSettings
                    || action == GestureAction.OpenNotificationDrawer
                    || action == GestureAction.LockScreen

        if (action != null && requiresAccessibilityService && !permissionsManager.checkPermissionOnce(
                PermissionGroup.Accessibility
            )
        ) {
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

            GestureAction.LaunchApp -> {
                val view = (context as Activity).window.decorView
                val options = ActivityOptionsCompat.makeScaleUpAnimation(
                    view,
                    0,
                    0,
                    view.width,
                    view.height
                )
                when (gesture) {
                    Gesture.SwipeLeft -> gestureState.value.swipeLeftApp
                    Gesture.SwipeRight -> gestureState.value.swipeRightApp
                    Gesture.SwipeDown -> gestureState.value.swipeDownApp
                    Gesture.LongPress -> gestureState.value.longPressApp
                    Gesture.DoubleTap -> gestureState.value.doubleTapApp
                    Gesture.HomeButton -> gestureState.value.homeButtonApp
                }?.launch(context, options.toBundle())
                true
            }

            else -> false
        }
    }

    fun dismissGestureFailedSheet() {
        failedGestureState = null
    }
}

data class GestureState(
    val swipeLeftAction: GestureAction = GestureAction.None,
    val swipeRightAction: GestureAction = GestureAction.None,
    val swipeDownAction: GestureAction = GestureAction.None,
    val longPressAction: GestureAction = GestureAction.None,
    val doubleTapAction: GestureAction = GestureAction.None,
    val homeButtonAction: GestureAction = GestureAction.None,
    val swipeLeftApp: SavableSearchable? = null,
    val swipeRightApp: SavableSearchable? = null,
    val swipeDownApp: SavableSearchable? = null,
    val longPressApp: SavableSearchable? = null,
    val doubleTapApp: SavableSearchable? = null,
    val homeButtonApp: SavableSearchable? = null,
)

data class FailedGesture(val gesture: Gesture, val action: GestureAction)