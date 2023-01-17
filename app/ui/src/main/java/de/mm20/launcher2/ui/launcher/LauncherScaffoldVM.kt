package de.mm20.launcher2.ui.launcher

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LauncherScaffoldVM : ViewModel(), KoinComponent {

    private val dataStore: LauncherDataStore by inject()

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
}