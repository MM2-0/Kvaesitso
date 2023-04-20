package de.mm20.launcher2.ui.settings.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CardsSettingsScreenVM: ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val opacity = dataStore.data.map { it.cards.opacity }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0f)
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

    val radius = dataStore.data.map { it.cards.radius }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

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

    val borderWidth = dataStore.data.map { it.cards.borderWidth }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)
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

    val shape = dataStore.data.map { it.cards.shape }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setShape(shape: Settings.CardSettings.Shape) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setCards(it.cards.toBuilder()
                        .setShape(shape)
                    ).build()
            }
        }
    }
}