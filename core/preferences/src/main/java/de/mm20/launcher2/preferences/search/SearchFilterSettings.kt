package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map

class SearchFilterSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val defaultFilter
        get() = launcherDataStore.data.map { it.searchFilter }
}