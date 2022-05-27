package de.mm20.launcher2.ui.launcher

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LauncherActivityVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val isEditFavoritesShown = MutableLiveData(false)

    private var isDarkInMode = MutableStateFlow(false)

    private val dimBackgroundState = combine(
        dataStore.data.map { it.appearance.dimWallpaper },
        isDarkInMode
    ) { dim, darkMode ->
        dim && darkMode
    }
    val dimBackground = dimBackgroundState.asLiveData()

    val lightStatusBar = combine(
        dimBackgroundState,
        dataStore.data.map { it.systemBars.lightStatusBar }
    ) { dim, light ->
        !dim && light
    }.asLiveData()

    val lightNavBar = combine(
        dimBackgroundState,
        dataStore.data.map { it.systemBars.lightNavBar }
    ) { dim, light ->
        !dim && light
    }.asLiveData()

    val hideNavBar = dataStore.data.map { it.systemBars.hideNavBar }.asLiveData()
    val hideStatusBar = dataStore.data.map { it.systemBars.hideStatusBar }.asLiveData()

    fun setDarkMode(darkMode: Boolean) {
        isDarkInMode.value = darkMode
    }

    fun showEditFavorites() {
        isEditFavoritesShown.value = true
    }

    fun hideEditFavorites() {
        isEditFavoritesShown.value = false
    }

    val layout = dataStore.data.map { it.appearance.layout }.asLiveData()
}