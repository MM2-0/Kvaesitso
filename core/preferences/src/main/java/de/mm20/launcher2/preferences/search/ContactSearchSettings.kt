package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ContactSearchSettings internal constructor(private val dataStore: LauncherDataStore) {
    val enabled: Flow<Boolean>
        get() = dataStore.data.map { it.contactSearchEnabled }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update { it.copy(contactSearchEnabled = enabled) }
    }
}