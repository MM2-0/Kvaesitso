package de.mm20.launcher2.ui.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ColorScheme
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Font
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppearanceSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    private val iconService: IconService by inject()

    val theme = dataStore.data.map { it.appearance.theme }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setTheme(theme: Theme) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(it.appearance.toBuilder().setTheme(theme))
                    .build()
            }
        }
    }

    val colorScheme = dataStore.data.map { it.appearance.colorScheme }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setColorScheme(colorScheme: ColorScheme) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(it.appearance.toBuilder().setColorScheme(colorScheme))
                    .build()
            }
        }
    }

    val font = dataStore.data.map { it.appearance.font }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setFont(font: Font) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(it.appearance.toBuilder().setFont(font))
                    .build()
            }
        }
    }
}