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

    val isHiddenItemsShown = MutableLiveData(false)
    val isEditFavoritesShown = MutableLiveData(false)

    private var isDarkInMode = MutableStateFlow(false)

    val dimBackground = combine(
        dataStore.data.map { it.appearance.dimWallpaper },
        isDarkInMode
    ) { dim, darkMode ->
        dim && darkMode
    }.asLiveData()

    fun setDarkMode(darkMode: Boolean) {
        isDarkInMode.value = darkMode
    }

    fun showEditFavorites() {
        isEditFavoritesShown.value = true
    }

    fun hideEditFavorites() {
        isEditFavoritesShown.value = false
    }


    fun showHiddenItems() {
        isHiddenItemsShown.value = true
    }

    fun hideHiddenItems() {
        isHiddenItemsShown.value = false
    }
}