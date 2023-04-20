package de.mm20.launcher2.ui.settings.wikipedia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WikipediaSettingsScreenVM: ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val wikipedia = dataStore.data.map { it.wikipediaSearch.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setWikipedia(wikipedia: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setWikipediaSearch(
                        it.wikipediaSearch.toBuilder()
                            .setEnabled(wikipedia)
                    )
                    .build()
            }
        }
    }

    val images = dataStore.data.map { it.wikipediaSearch.images }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setImages(images: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setWikipediaSearch(
                        it.wikipediaSearch.toBuilder()
                            .setImages(images)
                    )
                    .build()
            }
        }
    }

    val customUrl = dataStore.data.map { it.wikipediaSearch.customUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    fun setCustomUrl(customUrl: String) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setWikipediaSearch(
                        it.wikipediaSearch.toBuilder()
                            .setCustomUrl(customUrl)
                    )
                    .build()
            }
        }
    }
}