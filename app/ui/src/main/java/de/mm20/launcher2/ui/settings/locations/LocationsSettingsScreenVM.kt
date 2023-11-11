package de.mm20.launcher2.ui.settings.locations

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LocationsSettingsScreenVM: ViewModel(), KoinComponent {
    private val dataStore: LauncherDataStore by inject()

    val locations = dataStore.data.map { it.locationsSearch.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setLocations(openStreetMaps: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLocationsSearch(
                        it.locationsSearch.toBuilder()
                            .setEnabled(openStreetMaps)
                    )
                    .build()
            }
        }
    }

    val insaneUnits = dataStore.data.map { it.locationsSearch.useInsaneUnits }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setInsaneUnits(insaneUnits: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLocationsSearch(
                        it.locationsSearch.toBuilder()
                            .setUseInsaneUnits(insaneUnits)
                    )
                    .build()
            }
        }
    }

    val radius = dataStore.data.map { it.locationsSearch.searchRadius }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 1500)
    fun setRadius(radius: Int) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLocationsSearch(
                        it.locationsSearch.toBuilder()
                            .setSearchRadius(radius)
                    )
                    .build()
            }
        }
    }

    val customOverpassUrl = dataStore.data.map { it.locationsSearch.customOverpassUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    fun setCustomOverpassUrl(customUrl: String) {
        var customUrl = customUrl
        if (customUrl.endsWith('/'))
            customUrl = customUrl.substring(0, customUrl.length - 1)
        if (customUrl.endsWith("/api/interpreter"))
            customUrl = customUrl.substring(0, customUrl.length - "/api/interpreter".length)

        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLocationsSearch(
                        it.locationsSearch.toBuilder()
                            .setCustomOverpassUrl(customUrl)
                    )
                    .build()
            }
        }
    }

    val showMap = dataStore.data.map { it.locationsSearch.showMap }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setShowMap(showMap: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLocationsSearch(
                        it.locationsSearch.toBuilder()
                            .setShowMap(showMap)
                    )
                    .build()
            }
        }
    }

    val showPositionOnMap = dataStore.data.map { it.locationsSearch.showPositionOnMap }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setShowPositionOnMap(showPositionOnMap: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLocationsSearch(
                        it.locationsSearch.toBuilder()
                            .setShowPositionOnMap(showPositionOnMap)
                    )
                    .build()
            }
        }
    }

    val customTileServerUrl = dataStore.data.map { it.locationsSearch.customTileServerUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")
    fun setCustomTileServerUrl(customTileServerUrl: String) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setLocationsSearch(
                        it.locationsSearch.toBuilder()
                            .setCustomTileServerUrl(customTileServerUrl)
                    )
                    .build()
            }
        }
    }
}