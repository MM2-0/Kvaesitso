package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map

class SettingsSearchSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {

    val data
        get() = launcherDataStore.data.map {
            SettingsSearchSettingsData(
                enabled = it.settingsSearchEnabled,
            )
        }

    val enabled
        get() = launcherDataStore.data.map { it.settingsSearchEnabled }

    fun setEnabled(enabled: Boolean) {
        launcherDataStore.update {
            it.copy(settingsSearchEnabled = enabled)
        }
    }

}

data class SettingsSearchSettingsData(
    val enabled: Boolean = false,
)