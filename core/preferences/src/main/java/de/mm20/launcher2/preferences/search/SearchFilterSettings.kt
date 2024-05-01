package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.KeyboardFilterBarItem
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.SearchFilters
import kotlinx.coroutines.flow.map

class SearchFilterSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val defaultFilter
        get() = launcherDataStore.data.map { it.searchFilter }

    fun setDefaultFilter(filter: SearchFilters) {
        launcherDataStore.update {
            it.copy(searchFilter = filter)
        }
    }

    val filterBar
        get() = launcherDataStore.data.map { it.searchFilterBar }

    fun setFilterBar(filterBar: Boolean) {
        launcherDataStore.update {
            it.copy(searchFilterBar = filterBar)
        }
    }

    val filterBarItems
        get() = launcherDataStore.data.map { it.searchFilterBarItems.distinct() }

    fun setFilterBarItems(items: List<KeyboardFilterBarItem>) {
        launcherDataStore.update {
            it.copy(searchFilterBarItems = items)
        }
    }
}