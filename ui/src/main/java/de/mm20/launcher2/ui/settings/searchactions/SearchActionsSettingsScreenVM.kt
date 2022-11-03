package de.mm20.launcher2.ui.settings.searchactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.SearchActionSettings
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SearchActionsSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val searchActionSettings = dataStore.data.map { it.searchActions }.asLiveData()

    fun updateSettings(block: SearchActionSettings.Builder.() -> SearchActionSettings.Builder) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setSearchActions(
                        it.searchActions.toBuilder().block()
                    ).build()
            }
        }
    }
}