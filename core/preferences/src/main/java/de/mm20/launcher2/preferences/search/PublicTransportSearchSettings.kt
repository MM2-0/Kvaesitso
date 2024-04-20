package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PublicTransportSearchSettings internal constructor(
    private val launcherDataStore: LauncherDataStore
) {
    val enabledProviders: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.publicTransportSearchProviders }
}