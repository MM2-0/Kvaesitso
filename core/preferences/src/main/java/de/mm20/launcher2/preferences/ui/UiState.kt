package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class UiState internal constructor(
    private val launcherDataStore: LauncherDataStore,
){
    val favoritesTagsExpanded
        get() = launcherDataStore.data.map { it.stateTagsMultiline }.distinctUntilChanged()

    fun setFavoritesTagsExpanded(favoritesTagsExpanded: Boolean) {
        launcherDataStore.update {
            it.copy(stateTagsMultiline = favoritesTagsExpanded)
        }
    }
}