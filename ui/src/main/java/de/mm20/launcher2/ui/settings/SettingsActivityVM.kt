package de.mm20.launcher2.ui.settings

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SettingsActivityVM: ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val theme = dataStore.data.map { it.appearance.theme }.asLiveData()

    fun getTheme(): Settings.AppearanceSettings.Theme = runBlocking {
        dataStore.data.map { it.appearance.theme }.first()
    }

    val colorScheme = dataStore.data.map { it.appearance.colorScheme }.asLiveData()

    fun getColorScheme(): Settings.AppearanceSettings.ColorScheme = runBlocking {
        dataStore.data.map { it.appearance.colorScheme }.first()
    }
}