package de.mm20.launcher2.ui.settings.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LocationsSettingsScreenVM: ViewModel(), KoinComponent {
    private val settings: LocationSearchSettings by inject()

    val locations = settings.osmLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setLocations(openStreetMaps: Boolean) {
        settings.setOsmLocations(openStreetMaps)
    }

    val imperialUnits = settings.imperialUnits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setImperialUnits(imperialUnits: Boolean) {
        settings.setImperialUnits(imperialUnits)
    }

    val radius = settings.searchRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 1500)
    fun setRadius(radius: Int) {
        settings.setSearchRadius(radius)
    }

    val customOverpassUrl = settings.overpassUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    fun setCustomOverpassUrl(customUrl: String) {
        var url = customUrl
        if (url.endsWith('/')){
            url = url.substringBeforeLast('/')
        }
        if (url.endsWith("/api/interpreter")) {
            url = url.substringBeforeLast("/api/interpreter")
        }

        settings.setOverpassUrl(url)
    }

    val showMap = settings.showMap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setShowMap(showMap: Boolean) {
        settings.setShowMap(showMap)
    }

    val showPositionOnMap = settings.showPositionOnMap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setShowPositionOnMap(showPositionOnMap: Boolean) {
        settings.setShowPositionOnMap(showPositionOnMap)
    }

    val customTileServerUrl = settings.tileServer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setCustomTileServerUrl(customTileServerUrl: String) {
        settings.setTileServer(customTileServerUrl)
    }

    val hideUncategorized = settings.hideUncategorized
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setHideUncategorized(hideUncategorized: Boolean) {
        settings.setHideUncategorized(hideUncategorized)
    }

    val themeMap = settings.themeMap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setThemeMap(themeMap: Boolean) {
        settings.setThemeMap(themeMap)
    }
}