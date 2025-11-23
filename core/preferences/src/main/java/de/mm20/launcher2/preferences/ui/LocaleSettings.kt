package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.TimeFormat
import kotlinx.coroutines.flow.map


class LocaleSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val timeFormat
        get() = launcherDataStore.data.map { it.localeTimeFormat }

    fun setTimeFormat(timeFormat: TimeFormat) {
        launcherDataStore.update {
            it.copy(localeTimeFormat = timeFormat)
        }
    }
}