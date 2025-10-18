package de.mm20.launcher2.ui.settings.homescreen

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.GestureAction
import de.mm20.launcher2.preferences.ScreenOrientation
import de.mm20.launcher2.preferences.SearchBarColors
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.preferences.SystemBarColors
import de.mm20.launcher2.preferences.ui.ClockWidgetSettings
import de.mm20.launcher2.preferences.ui.GestureSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class HomescreenSettingsScreenVM(
    private val uiSettings: UiSettings,
    private val clockWidgetSettings: ClockWidgetSettings,
    private val gestureSettings: GestureSettings,
) : ViewModel() {

    var showClockWidgetSheet by mutableStateOf(false)


    val dimWallpaper = uiSettings.dimWallpaper
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setDimWallpaper(dimWallpaper: Boolean) {
        uiSettings.setDimWallpaper(dimWallpaper)
    }

    val blurWallpaper = uiSettings.blurWallpaper
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setBlurWallpaper(blurWallpaper: Boolean) {
        uiSettings.setBlurWallpaper(blurWallpaper)
    }

    val blurWallpaperRadius = uiSettings.wallpaperBlurRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 32)

    fun setBlurWallpaperRadius(blurWallpaperRadius: Int) {
        uiSettings.setWallpaperBlurRadius(blurWallpaperRadius)
    }

    fun openWallpaperChooser(context: AppCompatActivity) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null))
    }

    fun isBlurAvailable(context: Context): Boolean {
        if (!isAtLeastApiLevel(31)) return false
        return context.getSystemService<WindowManager>()?.isCrossWindowBlurEnabled == true
    }

    val statusBarIcons = uiSettings.statusBarColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLightStatusBar(statusBarColor: SystemBarColors) {
        uiSettings.setStatusBarColor(statusBarColor)
    }

    val navBarIcons = uiSettings.navigationBarColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setLightNavBar(navBarColors: SystemBarColors) {
        uiSettings.setNavigationBarColor(navBarColors)
    }

    val hideStatusBar = uiSettings.hideStatusBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setHideStatusBar(hideStatusBar: Boolean) {
        uiSettings.setHideStatusBar(hideStatusBar)
    }

    val hideNavBar = uiSettings.hideNavigationBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setHideNavBar(hideNavBar: Boolean) {
        uiSettings.setHideNavigationBar(hideNavBar)
    }

    val searchBarColor = uiSettings.searchBarColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setSearchBarColor(color: SearchBarColors) {
        uiSettings.setSearchBarColor(color)
    }

    val searchBarStyle = uiSettings.searchBarStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setSearchBarStyle(searchBarStyle: SearchBarStyle) {
        uiSettings.setSearchBarStyle(searchBarStyle)
    }

    val fixedSearchBar = uiSettings.fixedSearchBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setFixedSearchBar(fixedSearchBar: Boolean) {
        uiSettings.setFixedSearchBar(fixedSearchBar)
    }

    val bottomSearchBar = uiSettings.bottomSearchBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setBottomSearchBar(bottomSearchBar: Boolean) {
        uiSettings.setBottomSearchBar(bottomSearchBar)
    }

    val dock = uiSettings.dock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setDock(dock: Boolean) {
        uiSettings.setDock(dock)
    }

    val dockRows = uiSettings.dockRows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    fun setDockRows(rows: Int) {
        uiSettings.setDockRows(rows)
    }

    val fixedRotation = uiSettings.orientation.map { it != ScreenOrientation.Auto }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setFixedRotation(fixedRotation: Boolean) {
        uiSettings.setOrientation(if (fixedRotation) ScreenOrientation.Portrait else ScreenOrientation.Auto)
    }

    val widgetEditButton = uiSettings.widgetEditButton
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setWidgetEditButton(editButton: Boolean) {
        uiSettings.setWidgetEditButton(editButton)
    }

    val chargingAnimation = uiSettings.chargingAnimation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setChargingAnimation(chargingAnimation: Boolean) {
        uiSettings.setChargingAnimation(chargingAnimation)
    }

    val widgetsOnHomeScreen = uiSettings.homeScreenWidgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setWidgetsOnHomeScreen(widgetsOnHomeScreen: Boolean) {
        uiSettings.setHomeScreenWidgets(widgetsOnHomeScreen)
    }

    companion object : KoinComponent {
        val Factory = viewModelFactory {
            initializer {
                HomescreenSettingsScreenVM(
                    uiSettings = get(),
                    clockWidgetSettings = get(),
                    gestureSettings = get(),
                )
            }
        }
    }
}