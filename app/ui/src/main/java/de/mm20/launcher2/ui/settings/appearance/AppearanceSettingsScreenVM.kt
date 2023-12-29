package de.mm20.launcher2.ui.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Font
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.themes.ThemeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class AppearanceSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    private val iconService: IconService by inject()
    private val themeRepository: ThemeRepository by inject()

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

    val colorSchemeName = dataStore.data.map {
        it.appearance.themeId?.takeIf { it.isNotEmpty() }?.let {
            UUID.fromString(it)
        }
    }
        .flatMapLatest {
            themeRepository.getThemeOrDefault(it)
        }.map {
            it.name
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val compatMode = dataStore.data.map {
        it.appearance.forceCompatModeSystemColors
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    fun setCompatMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(it.appearance.toBuilder().setForceCompatModeSystemColors(enabled))
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