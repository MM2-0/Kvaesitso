package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class FavoritesSettingsData(
    val columns: Int,
    val frequentlyUsed: Boolean,
    val frequentlyUsedRows: Int,
    val latestRows: Int,
)

class FavoritesSettings internal constructor(
    private val dataStore: LauncherDataStore,
) : Flow<FavoritesSettingsData> by (dataStore.data.map {
    FavoritesSettingsData(
        columns = it.gridColumnCount,
        frequentlyUsed = it.favoritesFrequentlyUsed,
        frequentlyUsedRows = it.favoritesFrequentlyUsedRows,
        latestRows = it.favoritesLatestRows,
    )
}.distinctUntilChanged()) {

    val showEditButton
        get() = dataStore.data.map { it.favoritesEditButton }.distinctUntilChanged()

    fun setShowEditButton(showEditButton: Boolean) {
        dataStore.update { it.copy(favoritesEditButton = showEditButton) }
    }

    val showLatestButton
        get() = dataStore.data.map { it.favoritesLatestButton }.distinctUntilChanged()

    fun setShowLatestButton(showLatestButton: Boolean) {
        dataStore.update { it.copy(favoritesLatestButton = showLatestButton) }
    }

    val frequentlyUsed: Flow<Boolean>
        get() = dataStore.data.map { it.favoritesFrequentlyUsed }.distinctUntilChanged()

    fun setFrequentlyUsed(frequentlyUsed: Boolean) {
        dataStore.update { it.copy(favoritesFrequentlyUsed = frequentlyUsed) }
    }

    val frequentlyUsedRows: Flow<Int>
        get() = dataStore.data.map { it.favoritesFrequentlyUsedRows }.distinctUntilChanged()

    fun setFrequentlyUsedRows(frequentlyUsedRows: Int) {
        dataStore.update { it.copy(favoritesFrequentlyUsedRows = frequentlyUsedRows) }
    }

    val compactTags: Flow<Boolean>
        get() = dataStore.data.map { it.favoritesCompactTags }.distinctUntilChanged()

    fun setCompactTags(compactTags: Boolean) {
        dataStore.update { it.copy(favoritesCompactTags = compactTags) }
    }

    val latestRows: Flow<Int>
        get() = dataStore.data.map { it.favoritesLatestRows }.distinctUntilChanged()

    fun setLatestRows(latestRows: Int) {
        dataStore.update { it.copy(favoritesLatestRows = latestRows) }
    }
}