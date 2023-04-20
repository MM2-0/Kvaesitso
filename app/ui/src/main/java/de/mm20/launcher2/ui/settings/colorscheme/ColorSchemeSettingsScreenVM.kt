package de.mm20.launcher2.ui.settings.colorscheme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.AppearanceSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ColorSchemeSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val theme = dataStore.data.map { it.appearance.theme }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val colorScheme = dataStore.data.map { it.appearance.colorScheme }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setColorScheme(colorScheme: AppearanceSettings.ColorScheme) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(
                        it.appearance.toBuilder()
                            .setColorScheme(colorScheme)
                    ).build()
            }
        }
    }
}