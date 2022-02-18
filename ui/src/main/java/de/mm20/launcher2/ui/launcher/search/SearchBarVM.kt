package de.mm20.launcher2.ui.launcher.search

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SearchBarVM: ViewModel() {
    val focused = MutableLiveData(false)

    fun setFocused(focused :Boolean) {
        this.focused.value = focused
    }
}