package de.mm20.launcher2.ui.launcher

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import de.mm20.launcher2.ktx.isBrightColor
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.ui.launcher.search.SearchBarLevel
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LauncherScaffoldVM : ViewModel(), KoinComponent {
    val isSearchOpen = MutableLiveData(false)
    val blurBackground = MutableLiveData(false)

    val statusBarColor = MutableLiveData(0)
    val darkStatusBarIcons = MutableLiveData(false)

    val searchBarLevel = MutableLiveData(SearchBarLevel.Resting)

    val dataStore: LauncherDataStore by inject()

    val hideStatusBar = dataStore.data.map { it.systemBars.hideStatusBar }.asLiveData()
    val hideNavBar = dataStore.data.map { it.systemBars.hideNavBar }.asLiveData()

    val autoFocus = dataStore.data.map { it.searchBar.autoFocus }.asLiveData()

    var scrollY = 0
        set(value) {
            if (value == 0 && field != 0) {
                if (isSearchOpen.value == true) {
                    searchBarLevel.value = SearchBarLevel.Active
                    blurBackground.value = true
                } else {
                    searchBarLevel.value = SearchBarLevel.Resting
                    blurBackground.value = false
                }
            } else if (value > 0 && field == 0) {
                searchBarLevel.value = SearchBarLevel.Raised
                blurBackground.value = true
            }
            field = value
        }

    fun openSearch() {
        if (isSearchOpen.value == true) return
        isSearchOpen.value = true
        if (scrollY == 0) {
            searchBarLevel.value = SearchBarLevel.Active
            blurBackground.value = true
        }
    }

    fun closeSearch() {
        if (isSearchOpen.value == false) return
        isSearchOpen.value = false
        if (scrollY == 0) {
            searchBarLevel.value = SearchBarLevel.Resting
            blurBackground.value = false
        }
    }

    fun toggleSearch() {
        if (isSearchOpen.value == true) closeSearch()
        else openSearch()
    }

    fun setStatusBarColor(color: Int) {
        statusBarColor.value = color
        darkStatusBarIcons.value = color.isBrightColor()
    }
}