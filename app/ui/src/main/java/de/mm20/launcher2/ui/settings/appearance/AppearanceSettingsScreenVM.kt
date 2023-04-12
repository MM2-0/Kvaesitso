package de.mm20.launcher2.ui.settings.appearance

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.icons.IconPack
import de.mm20.launcher2.icons.IconService
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ColorScheme
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Font
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.preferences.Settings.SearchBarSettings
import de.mm20.launcher2.preferences.Settings.SearchBarSettings.SearchBarColors
import de.mm20.launcher2.preferences.Settings.SystemBarsSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppearanceSettingsScreenVM : ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    private val iconService: IconService by inject()

    val theme = dataStore.data.map { it.appearance.theme }.asLiveData()
    fun setTheme(theme: Theme) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(it.appearance.toBuilder().setTheme(theme))
                    .build()
            }
        }
    }

    val colorScheme = dataStore.data.map { it.appearance.colorScheme }.asLiveData()
    fun setColorScheme(colorScheme: ColorScheme) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setAppearance(it.appearance.toBuilder().setColorScheme(colorScheme))
                    .build()
            }
        }
    }

    val font = dataStore.data.map { it.appearance.font }.asLiveData()
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