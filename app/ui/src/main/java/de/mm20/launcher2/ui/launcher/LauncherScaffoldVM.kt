package de.mm20.launcher2.ui.launcher

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.globalactions.GlobalActionsService
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.BaseLayout
import de.mm20.launcher2.preferences.ColorScheme
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.ScreenOrientation
import de.mm20.launcher2.preferences.SearchBarColors
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.ui.gestures.Gesture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LauncherScaffoldVM : ViewModel(), KoinComponent {

    private val uiSettings: UiSettings by inject()
    private val gestureSettings: GestureSettings by inject()
    private val globalActionsService: GlobalActionsService by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val searchableRepository: SavableSearchableRepository by inject()

    private var isSystemInDarkMode = MutableStateFlow(false)

    private val dimBackgroundState = combine(
        uiSettings.dimWallpaper,
        uiSettings.colorScheme,
        isSystemInDarkMode
    ) { dim, theme, systemDarkMode ->
        dim && (theme == ColorScheme.Dark || theme == ColorScheme.System && systemDarkMode)
    }
    val dimBackground = dimBackgroundState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val statusBarColor = uiSettings.statusBarColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    val navBarColor = uiSettings.navigationBarColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val chargingAnimation = uiSettings.chargingAnimation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val hideNavBar = uiSettings.hideNavigationBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val hideStatusBar = uiSettings.hideStatusBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setSystemInDarkMode(darkMode: Boolean) {
        isSystemInDarkMode.value = darkMode
    }

    val baseLayout = uiSettings.baseLayout
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val bottomSearchBar = uiSettings.bottomSearchBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val reverseSearchResults = uiSettings.reverseSearchResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val fixedSearchBar = uiSettings.fixedSearchBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val fixedRotation = uiSettings.orientation
        .map { it != ScreenOrientation.Auto }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val isSearchOpen = mutableStateOf(false)
    val isWidgetEditMode = mutableStateOf(false)

    val searchBarFocused = mutableStateOf(false)

    val autoFocusSearch = uiSettings.openKeyboardOnSearch

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

    val wallpaperBlur = uiSettings.blurWallpaper
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val wallpaperBlurRadius = uiSettings.wallpaperBlurRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 32)


    val fillClockHeight = uiSettings.clockFillScreen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)
    val searchBarColor = uiSettings.searchBarColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SearchBarColors.Auto)
    val searchBarStyle = uiSettings.searchBarStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SearchBarStyle.Transparent)

    val gestureState: StateFlow<GestureState> = gestureSettings
        .combine(baseLayout) { settings, layout ->
            val swipeLeftAction =
                settings.swipeLeft.takeIf { layout != BaseLayout.Pager } ?: GestureAction.NoAction
            val swipeRightAction = settings.swipeRight.takeIf { layout != BaseLayout.PagerReversed }
                ?: GestureAction.NoAction
            val swipeDownAction =
                settings.swipeDown.takeIf { layout != BaseLayout.PullDown } ?: GestureAction.NoAction
            val longPressAction = settings.longPress
            val doubleTapAction = settings.doubleTap
            val homeButtonAction = settings.homeButton

            val swipeLeftAppKey =
                if (swipeLeftAction is GestureAction.Launch) swipeLeftAction.key else null
            val swipeRightAppKey =
                if (swipeRightAction is GestureAction.Launch) swipeRightAction.key else null
            val swipeDownAppKey =
                if (swipeDownAction is GestureAction.Launch) swipeDownAction.key else null
            val longPressAppKey =
                if (longPressAction is GestureAction.Launch) longPressAction.key else null
            val doubleTapAppKey =
                if (doubleTapAction is GestureAction.Launch) doubleTapAction.key else null
            val homeButtonAppKey =
                if (homeButtonAction is GestureAction.Launch) homeButtonAction.key else null
            val apps = listOfNotNull(
                swipeLeftAppKey,
                swipeRightAppKey,
                swipeDownAppKey,
                longPressAppKey,
                doubleTapAppKey,
                homeButtonAppKey,
            ).let { searchableRepository.getByKeys(it).first() }

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
            Gesture.SwipeDown -> gestureState.value.swipeDownAction.takeIf { baseLayout.value != BaseLayout.PullDown }
            Gesture.SwipeLeft -> gestureState.value.swipeLeftAction.takeIf { baseLayout.value != BaseLayout.Pager }
            Gesture.SwipeRight -> gestureState.value.swipeRightAction.takeIf { baseLayout.value != BaseLayout.PagerReversed }
            Gesture.HomeButton -> gestureState.value.homeButtonAction
        }
        val requiresAccessibilityService =
            action is GestureAction.Recents
                    || action is GestureAction.PowerMenu
                    || action is GestureAction.QuickSettings
                    || action is GestureAction.Notifications
                    || action is GestureAction.ScreenLock

        if (action != null && requiresAccessibilityService && !permissionsManager.checkPermissionOnce(
                PermissionGroup.Accessibility
            )
        ) {
            failedGestureState = FailedGesture(gesture, action)
            return true
        }


        return when (action) {
            is GestureAction.Search -> {
                openSearch()
                true
            }

            is GestureAction.Notifications -> {
                globalActionsService.openNotificationDrawer()
                true
            }

            is GestureAction.QuickSettings -> {
                globalActionsService.openQuickSettings()
                true
            }

            is GestureAction.ScreenLock -> {
                globalActionsService.lockScreen()
                true
            }

            is GestureAction.PowerMenu -> {
                globalActionsService.openPowerDialog()
                true
            }

            is GestureAction.Recents -> {
                globalActionsService.openRecents()
                true
            }

            is GestureAction.Launch -> {
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
    val swipeLeftAction: GestureAction = GestureAction.NoAction,
    val swipeRightAction: GestureAction = GestureAction.NoAction,
    val swipeDownAction: GestureAction = GestureAction.NoAction,
    val longPressAction: GestureAction = GestureAction.NoAction,
    val doubleTapAction: GestureAction = GestureAction.NoAction,
    val homeButtonAction: GestureAction = GestureAction.NoAction,
    val swipeLeftApp: SavableSearchable? = null,
    val swipeRightApp: SavableSearchable? = null,
    val swipeDownApp: SavableSearchable? = null,
    val longPressApp: SavableSearchable? = null,
    val doubleTapApp: SavableSearchable? = null,
    val homeButtonApp: SavableSearchable? = null,
)

data class FailedGesture(val gesture: Gesture, val action: GestureAction)