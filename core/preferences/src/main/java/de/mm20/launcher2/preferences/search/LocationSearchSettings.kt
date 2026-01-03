package de.mm20.launcher2.preferences.search

import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.MeasurementSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocationSearchSettings internal constructor(
    private val launcherDataStore: LauncherDataStore,
) {
    val data
        get() = launcherDataStore.data.map {
            LocationSearchSettingsData(
                providers = it.locationSearchProviders,
                searchRadius = it.locationSearchRadius,
                hideUncategorized = it.locationSearchHideUncategorized,
                overpassUrl = it.locationSearchOverpassUrl,
                tileServer = it.locationSearchTileServer,
                measurementSystem = it.localeMeasurementSystem,
                showMap = it.locationSearchShowMap,
                showPositionOnMap = it.locationSearchShowPositionOnMap,
                themeMap = it.locationSearchThemeMap,
            )
        }

    val enabledProviders: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.locationSearchProviders }

    val osmLocations
        get() = launcherDataStore.data.map { it.locationSearchProviders.contains("openstreetmaps") }

    fun setOsmLocations(osmLocations: Boolean) {
        launcherDataStore.update {
            if (osmLocations) {
                it.copy(locationSearchProviders = it.locationSearchProviders + "openstreetmaps")
            } else {
                it.copy(locationSearchProviders = it.locationSearchProviders - "openstreetmaps")
            }
        }
    }

    val enabledPlugins: Flow<Set<String>>
        get() = launcherDataStore.data.map { it.locationSearchProviders - "openstreetmaps" }

    fun setPluginEnabled(authority: String, enabled: Boolean) {
        launcherDataStore.update {
            if (enabled) {
                it.copy(locationSearchProviders = it.locationSearchProviders + authority)
            } else {
                it.copy(locationSearchProviders = it.locationSearchProviders - authority)
            }
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

    fun setOverpassUrl(overpassUrl: String?) {
        var url = overpassUrl
        if (url.isNullOrBlank()) {
            url = DefaultOverpassUrl
        } else {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            if (url.endsWith('/')) {
                url = url.substringBeforeLast('/')
            }
            if (url.endsWith("/api/interpreter")) {
                url = url.substringBeforeLast("/api/interpreter")
            }
        }
        launcherDataStore.update {
            it.copy(locationSearchOverpassUrl = url)
        }
    }

    val tileServer
        get() = launcherDataStore.data.map { it.locationSearchTileServer }

    fun setTileServer(tileServer: String?) {
        var url = tileServer
        if (url.isNullOrBlank()) {
            url = DefaultTileServerUrl
        } else {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            if (!url.contains("\${z}") || !url.contains("\${x}") || !url.contains("\${y}")) {
                url = "$url/\${z}/\${x}/\${y}.png"
            }
        }
        launcherDataStore.update {
            it.copy(locationSearchTileServer = url)
        }
    }

    val showMap
        get() = launcherDataStore.data.map { it.locationSearchShowMap }

    fun setShowMap(showMap: Boolean) {
        launcherDataStore.update {
            it.copy(locationSearchShowMap = showMap)
        }
    }

    val themeMap
        get() = launcherDataStore.data.map { it.locationSearchThemeMap }

    fun setThemeMap(themeMap: Boolean) {
        launcherDataStore.update {
            it.copy(locationSearchThemeMap = themeMap)
        }
    }

    val measurementSystem
        get() = launcherDataStore.data.map { it.localeMeasurementSystem }

    companion object {
        const val DefaultTileServerUrl = "https://tile.openstreetmap.org/\${z}/\${x}/\${y}.png"
        const val DefaultOverpassUrl = "https://overpass-api.de"
    }

}

data class LocationSearchSettingsData(
    val providers: Set<String> = setOf("openstreetmaps"),
    val searchRadius: Int = 1500,
    val hideUncategorized: Boolean = true,
    val overpassUrl: String? = null,
    val tileServer: String? = null,
    val measurementSystem: MeasurementSystem = MeasurementSystem.System,
    val showMap: Boolean = false,
    val showPositionOnMap: Boolean = false,
    val themeMap: Boolean = true,
)