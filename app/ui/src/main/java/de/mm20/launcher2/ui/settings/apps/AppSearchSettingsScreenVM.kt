package de.mm20.launcher2.ui.settings.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.ui.SearchUiSettings
import de.mm20.launcher2.preferences.ui.UiSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppSearchSettingsScreenVM: ViewModel(), KoinComponent {
    private val searchUiSettings: SearchUiSettings by inject()
    private val uiSettings: UiSettings by inject()

    val allApps = searchUiSettings.allApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setAllApps(allApps: Boolean) {
        searchUiSettings.setAllApps(allApps)
    }

    val showList = uiSettings.gridSettings.map { it.showList }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setShowList(showList: Boolean) {
        uiSettings.setGridShowList(showList)
    }

    val showListIcons = uiSettings.gridSettings.map { it.showListIcons }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setShowListIcons(showIcons: Boolean) {
        uiSettings.setGridShowListIcons(showIcons)
    }
}