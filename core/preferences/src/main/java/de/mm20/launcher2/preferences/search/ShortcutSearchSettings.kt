package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ShortcutSearchSettings internal constructor(
    private val dataStore: LauncherDataStore
) {
    val enabled
        get() = dataStore.data.map { it.shortcutSearchEnabled }.distinctUntilChanged()

    /**
     * Set of blocked packages that should not be shown in the search results.
     * Format: packageName:userId
     */
    val blocklist
        get() = dataStore.data.map { it.shortcutSearchBlocklist }.distinctUntilChanged()

    fun setEnabled(enabled: Boolean) {
        dataStore.update {
            it.copy(shortcutSearchEnabled = enabled)
        }
    }

    fun setBlocklist(blocklist: Set<String>) {
        dataStore.update {
            it.copy(shortcutSearchBlocklist = blocklist)
        }
    }
}