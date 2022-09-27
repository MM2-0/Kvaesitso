package de.mm20.launcher2.ui.settings.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FavoritesSettingsScreenVM: ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val frequentlyUsed = dataStore.data.map { it.favorites.frequentlyUsed }.asLiveData()
    fun setFrequentlyUsed(frequentlyUsed: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setFavorites(
                        it.favorites.toBuilder()
                            .setFrequentlyUsed(frequentlyUsed)
                    )
                    .build()
            }
        }
    }

    val frequentlyUsedRows = dataStore.data.map { it.favorites.frequentlyUsedRows }.asLiveData()
    fun setFrequentlyUsedRows(frequentlyUsedRows: Int) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setFavorites(
                        it.favorites.toBuilder()
                            .setFrequentlyUsedRows(frequentlyUsedRows)
                    )
                    .build()
            }
        }
    }

    val editButton = dataStore.data.map { it.favorites.editButton }.asLiveData()
    fun setEditButton(editButton: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setFavorites(
                        it.favorites.toBuilder()
                            .setEditButton(editButton)
                    )
                    .build()
            }
        }
    }
}