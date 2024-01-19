package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map

class CalendarSearchSettings internal constructor(
    private val dataStore: LauncherDataStore,
){
    val enabled
        get() = dataStore.data.map { it.calendarSearchEnabled }

    fun setEnabled(enabled: Boolean) {
        dataStore.update {
            it.copy(calendarSearchEnabled = enabled)
        }
    }
}