package de.mm20.launcher2.ui.launcher

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LauncherActivityVM : ViewModel() {
    val isHiddenItemsShown = MutableLiveData(false)
    val isEditFavoritesShown = MutableLiveData(false)
    val dimBackground = MutableLiveData(false)

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