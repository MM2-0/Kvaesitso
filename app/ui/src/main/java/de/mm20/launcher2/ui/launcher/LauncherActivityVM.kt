package de.mm20.launcher2.ui.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LauncherActivityVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    private var isSystemInDarkMode = MutableStateFlow(false)

    private val dimBackgroundState = combine(
        dataStore.data.map { it.appearance.dimWallpaper },
        dataStore.data.map { it.appearance.theme },
        isSystemInDarkMode
    ) { dim, theme, systemDarkMode ->
        dim && (theme == Theme.Dark || theme == Theme.System && systemDarkMode)
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
}