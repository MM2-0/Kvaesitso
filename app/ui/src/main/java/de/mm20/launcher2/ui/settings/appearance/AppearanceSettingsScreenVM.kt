package de.mm20.launcher2.ui.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.preferences.ColorScheme
import de.mm20.launcher2.preferences.Font
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.ThemeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppearanceSettingsScreenVM : ViewModel(), KoinComponent {
    private val uiSettings: UiSettings by inject()

    private val themeRepository: ThemeRepository by inject()

    val colorScheme = uiSettings.colorScheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setColorScheme(colorScheme: ColorScheme) {
        uiSettings.setColorScheme(colorScheme)
    }

    val themeName = uiSettings.theme.flatMapLatest {
            themeRepository.getThemeOrDefault(it)
        }.map {
            it.name
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)


    val font = uiSettings.font
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setFont(font: Font) {
        uiSettings.setFont(font)
    }
}