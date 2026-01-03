package de.mm20.launcher2.preferences.ui

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SearchUiSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val launchOnEnter
        get() = launcherDataStore.data.map { it.searchLaunchOnEnter }.distinctUntilChanged()

    fun setLaunchOnEnter(launchOnEnter: Boolean) {
        launcherDataStore.update {
            it.copy(searchLaunchOnEnter = launchOnEnter)
        }
    }

    val hiddenItemsButton
        get() = launcherDataStore.data.map { it.hiddenItemsShowButton }.distinctUntilChanged()

    fun setHiddenItemsButton(hiddenItemsButton: Boolean) {
        launcherDataStore.update {
            it.copy(hiddenItemsShowButton = hiddenItemsButton)
        }
    }

    val favorites
        get() = launcherDataStore.data.map { it.favoritesEnabled }.distinctUntilChanged()

    fun setFavorites(favorites: Boolean) {
        launcherDataStore.update {
            it.copy(favoritesEnabled = favorites)
        }
    }

    val allApps
        get() = launcherDataStore.data.map { it.searchAllApps }.distinctUntilChanged()

    fun setAllApps(allAppsGrid: Boolean) {
        launcherDataStore.update {
            it.copy(searchAllApps = allAppsGrid)
        }
    }

    val openKeyboard
        get() = launcherDataStore.data.map { it.searchBarKeyboard }.distinctUntilChanged()

    fun setOpenKeyboard(openKeyboard: Boolean) {
        launcherDataStore.update {
            it.copy(searchBarKeyboard = openKeyboard)
        }
    }

    val reversedResults
        get() = launcherDataStore.data.map { it.searchResultsReversed }.distinctUntilChanged()

    fun setReversedResults(reversedResults: Boolean) {
        launcherDataStore.update {
            it.copy(searchResultsReversed = reversedResults)
        }
    }

    val separateWorkProfile
        get() = launcherDataStore.data.map { it.separateWorkProfile }.distinctUntilChanged()

    fun setSeparateWorkProfile(separateWorkProfile: Boolean) {
        launcherDataStore.update {
            it.copy(separateWorkProfile = separateWorkProfile)
        }
    }

}