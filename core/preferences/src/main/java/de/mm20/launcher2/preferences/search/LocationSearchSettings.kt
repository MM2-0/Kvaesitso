package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.map

class LocationSearchSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {

    val data
        get() = launcherDataStore.data.map {
            LocationSearchSettingsData(
                enabled = it.locationSearchEnabled,
                searchRadius = it.locationSearchRadius,
                hideUncategorized = it.locationSearchHideUncategorized,
                overpassUrl = it.locationSearchOverpassUrl,
                tileServer = it.locationSearchTileServer,
                imperialUnits = it.locationSearchImperialUnits,
                showMap = it.locationSearchShowMap,
                showPositionOnMap = it.locationSearchShowPositionOnMap,
                themeMap = it.locationSearchThemeMap,
            )
        }

    val enabled
        get() = launcherDataStore.data.map { it.locationSearchEnabled }

    fun setEnabled(enabled: Boolean) {
        launcherDataStore.update {
            it.copy(locationSearchEnabled = enabled)
        }
    }

    val searchRadius
        get() = launcherDataStore.data.map { it.locationSearchRadius }

    fun setSearchRadius(searchRadius: Int) {
        launcherDataStore.update {
            it.copy(locationSearchRadius = searchRadius)
        }
    }

    val hideUncategorized
        get() = launcherDataStore.data.map { it.locationSearchHideUncategorized }

    fun setHideUncategorized(hideUncategorized: Boolean) {
        launcherDataStore.update {
            it.copy(locationSearchHideUncategorized = hideUncategorized)
        }
    }

    val overpassUrl
        get() = launcherDataStore.data.map { it.locationSearchOverpassUrl }

    fun setOverpassUrl(overpassUrl: String) {
        launcherDataStore.update {
            it.copy(locationSearchOverpassUrl = overpassUrl)
        }
    }

    val tileServer
        get() = launcherDataStore.data.map { it.locationSearchTileServer }

    fun setTileServer(tileServer: String) {
        launcherDataStore.update {
            it.copy(locationSearchTileServer = tileServer)
        }
    }

    val showMap
        get() = launcherDataStore.data.map { it.locationSearchShowMap }

    fun setShowMap(showMap: Boolean) {
        launcherDataStore.update {
            it.copy(locationSearchShowMap = showMap)
        }
    }

    val showPositionOnMap
        get() = launcherDataStore.data.map { it.locationSearchShowPositionOnMap }

    fun setShowPositionOnMap(showPositionOnMap: Boolean) {
        launcherDataStore.update {
            it.copy(locationSearchShowPositionOnMap = showPositionOnMap)
        }
    }

    val themeMap
        get() = launcherDataStore.data.map { it.locationSearchThemeMap }

    fun setThemeMap(themeMap: Boolean) {
        launcherDataStore.update {
            it.copy(locationSearchThemeMap = themeMap)
        }
    }

    val imperialUnits
        get() = launcherDataStore.data.map { it.locationSearchImperialUnits }

    fun setImperialUnits(imperialUnits: Boolean) {
        launcherDataStore.update {
            it.copy(locationSearchImperialUnits = imperialUnits)
        }
    }

    companion object {
        const val DefaultTileServerUrl = "https://tile.openstreetmap.org"
        const val DefaultOverpassUrl = "https://overpass-api.de/"
    }

}

data class LocationSearchSettingsData(
    val enabled: Boolean = false,
    val searchRadius: Int = 1500,
    val hideUncategorized: Boolean = true,
    val overpassUrl: String = LocationSearchSettings.DefaultOverpassUrl,
    val tileServer: String = LocationSearchSettings.DefaultTileServerUrl,
    val imperialUnits: Boolean = false,
    val showMap: Boolean = false,
    val showPositionOnMap: Boolean = false,
    val themeMap: Boolean = true,
)