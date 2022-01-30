package de.mm20.launcher2.ui.settings.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CardsSettingsScreenVM: ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val opacity = dataStore.data.map { it.cards.opacity }.asLiveData()
    fun setOpacity(opacity: Float) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setCards(it.cards.toBuilder()
                        .setOpacity(opacity)
                    ).build()
            }
        }
    }

    val radius = dataStore.data.map { it.cards.radius }.asLiveData()
    fun setRadius(radius: Int) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setCards(it.cards.toBuilder()
                        .setRadius(radius)
                    ).build()
            }
        }
    }

    val borderWidth = dataStore.data.map { it.cards.borderWidth }.asLiveData()
    fun setBorderWidth(borderWidth: Int) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setCards(it.cards.toBuilder()
                        .setBorderWidth(borderWidth)
                    ).build()
            }
        }
    }
}