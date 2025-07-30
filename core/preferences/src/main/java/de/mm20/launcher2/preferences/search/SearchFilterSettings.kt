package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.KeyboardFilterBarItem
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.SearchFilters
import kotlinx.coroutines.flow.map

class SearchFilterSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val onlineResultsWiFi
        get() = launcherDataStore.data.map { it.onlineResultsWifi }

    fun setOnlineResultsWiFi(wifi: Boolean) {
        launcherDataStore.update {
            it.copy(onlineResultsWifi = wifi)
        }
    }

    val onlineResultsMobile
        get() = launcherDataStore.data.map { it.onlineResultsMobile }

    fun setOnlineResultsMobile(mobile: Boolean) {
        launcherDataStore.update {
            it.copy(onlineResultsMobile = mobile)
        }
    }

}