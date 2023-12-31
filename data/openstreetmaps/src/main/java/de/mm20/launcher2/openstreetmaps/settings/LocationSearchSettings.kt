package de.mm20.launcher2.openstreetmaps.settings

import android.content.Context
import de.mm20.launcher2.settings.BaseSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocationSearchSettings(
    private val context: Context
): BaseSettings<LocationSearchSettingsData>(
    context = context,
    fileName = "osm_search.json",
    serializer = LocationSearchSettingsDataSerializer(context),
    migrations = emptyList(),
) {

    internal val data
        get() = context.dataStore.data

    val enabled: Flow<Boolean>
        get() = context.dataStore.data.map { it.enabled }

    fun setEnabled(enabled: Boolean) {
        updateData {
            it.copy(enabled = enabled)
        }
    }

    val imperialUnits: Flow<Boolean>
        get() = context.dataStore.data.map { it.imperialUnits }

    fun setImperialUnits(imperialUnits: Boolean) {
        updateData {
            it.copy(imperialUnits = imperialUnits)
        }
    }

    val searchRadius: Flow<Int>
        get() = context.dataStore.data.map { it.searchRadius }

    fun setSearchRadius(searchRadius: Int) {
        updateData {
            it.copy(searchRadius = searchRadius)
        }
    }

    val hideUncategorized: Flow<Boolean>
        get() = context.dataStore.data.map { it.hideUncategorized }

    fun setHideUncategorized(hideUncategorized: Boolean) {
        updateData {
            it.copy(hideUncategorized = hideUncategorized)
        }
    }

    val overpassUrl: Flow<String>
        get() = context.dataStore.data.map { it.overpassUrl }

    fun setOverpassUrl(overpassUrl: String) {
        updateData {
            it.copy(overpassUrl = overpassUrl)
        }
    }

    val tileServer: Flow<String>
        get() = context.dataStore.data.map { it.tileServer }

    fun setTileServer(tileServer: String) {
        updateData {
            it.copy(tileServer = tileServer)
        }
    }

    val showMap: Flow<Boolean>
        get() = context.dataStore.data.map { it.showMap }

    fun setShowMap(showMap: Boolean) {
        updateData {
            it.copy(showMap = showMap)
        }
    }

    val showPositionOnMap: Flow<Boolean>
        get() = context.dataStore.data.map { it.showPositionOnMap }

    fun setShowPositionOnMap(showPositionOnMap: Boolean) {
        updateData {
            it.copy(showPositionOnMap = showPositionOnMap)
        }
    }

    val themeMap: Flow<Boolean>
        get() = context.dataStore.data.map { it.themeMap }

    fun setThemeMap(themeMap: Boolean) {
        updateData {
            it.copy(themeMap = themeMap)
        }
    }

    companion object {
        const val DefaultTileServerUrl = "https://tile.openstreetmap.org"
        const val DefaultOverpassUrl = "https://overpass-api.de/"
    }
}