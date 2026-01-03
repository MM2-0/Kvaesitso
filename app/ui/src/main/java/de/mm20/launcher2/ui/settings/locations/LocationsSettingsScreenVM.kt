package de.mm20.launcher2.ui.settings.locations

import android.icu.util.LocaleData
import android.icu.util.ULocale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugins.PluginService
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.preferences.search.LocationSearchSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LocationsSettingsScreenVM : ViewModel(), KoinComponent {
    private val settings: LocationSearchSettings by inject()
    private val pluginService: PluginService by inject()

    val availablePlugins = pluginService.getPluginsWithState(
        type = PluginType.LocationSearch,
        enabled = true,
    )
    val enabledPlugins = settings.enabledPlugins

    val osmLocations = settings.osmLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setOsmLocations(osmLocations: Boolean) {
        settings.setOsmLocations(osmLocations)
    }

    val measurementSystem = settings.measurementSystem
        .map { ms ->
            if (ms == MeasurementSystem.System) {
                return@map if (isAtLeastApiLevel(28)) {
                    val systemMs = LocaleData.getMeasurementSystem(ULocale.getDefault())
                    when(systemMs) {
                        LocaleData.MeasurementSystem.UK -> MeasurementSystem.UnitedKingdom
                        LocaleData.MeasurementSystem.US -> MeasurementSystem.UnitedStates
                        else -> MeasurementSystem.Metric
                    }
                } else {
                    MeasurementSystem.Metric
                }
            }
            return@map ms
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), MeasurementSystem.Metric)

    val radius = settings.searchRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 1500)

    fun setRadius(radius: Int) {
        settings.setSearchRadius(radius)
    }

    val showMap = settings.showMap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setShowMap(showMap: Boolean) {
        settings.setShowMap(showMap)
    }

    val customTileServerUrl = settings.tileServer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setCustomTileServerUrl(customTileServerUrl: String) {
        settings.setTileServer(customTileServerUrl.takeIf { it.isNotBlank() })
    }

    val themeMap = settings.themeMap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setThemeMap(themeMap: Boolean) {
        settings.setThemeMap(themeMap)
    }

    fun setPluginEnabled(authority: String, enabled: Boolean) {
        settings.setPluginEnabled(authority, enabled)
    }
}