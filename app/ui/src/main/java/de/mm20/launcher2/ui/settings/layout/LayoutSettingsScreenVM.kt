package de.mm20.launcher2.ui.settings.layout

import android.content.pm.ActivityInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.LayoutSettings.Layout
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LayoutSettingsScreenVM: ViewModel(), KoinComponent {

    private val dataStore : LauncherDataStore by inject()

    val baseLayout = dataStore.data.map { it.layout.baseLayout }.asLiveData()
    fun setBaseLayout(baseLayout: Layout) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLayout(it.layout.toBuilder().setBaseLayout(baseLayout))
                    .build()
            }
        }
    }

    val bottomSearchBar = dataStore.data.map { it.layout.bottomSearchBar }.asLiveData()
    fun setBottomSearchBar(bottomSearchBar: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLayout(it.layout.toBuilder().setBottomSearchBar(bottomSearchBar))
                    .build()
            }
        }
    }

    val reverseSearchResults = dataStore.data.map { it.layout.reverseSearchResults }.asLiveData()
    fun setReverseSearchResults(reverseSearchResults: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLayout(it.layout.toBuilder().setReverseSearchResults(reverseSearchResults))
                    .build()
            }
        }
    }

    val fixedSearchBar = dataStore.data.map { it.layout.fixedSearchBar }.asLiveData()
    fun setFixedSearchBar(fixedSearchBar: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLayout(it.layout.toBuilder().setFixedSearchBar(fixedSearchBar))
                    .build()
            }
        }
    }

    val fixedRotation = dataStore.data.map { it.layout.fixedRoation }.asLiveData()
    fun setFixedRotation(fixedRotation: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLayout(it.layout.toBuilder().setFixedRotation(fixedRotation))
                    .build()
            }
        }
    }
}