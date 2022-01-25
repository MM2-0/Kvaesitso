package de.mm20.launcher2.ui.launcher

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.ktx.isBrightColor
import de.mm20.launcher2.ui.launcher.search.SearchBarLevel

class LauncherScaffoldVM : ViewModel() {
    val isSearchOpen = MutableLiveData(false)
    val blurBackground = MutableLiveData(false)

    val statusBarColor = MutableLiveData(0)
    val darkStatusBarIcons = MutableLiveData(false)

    val searchBarLevel = MutableLiveData(SearchBarLevel.Resting)

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