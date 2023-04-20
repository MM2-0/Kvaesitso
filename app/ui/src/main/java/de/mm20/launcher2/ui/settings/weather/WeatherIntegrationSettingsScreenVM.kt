package de.mm20.launcher2.ui.settings.weather

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.WeatherSettings
import de.mm20.launcher2.weather.WeatherLocation
import de.mm20.launcher2.weather.WeatherRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WeatherIntegrationSettingsScreenVM : ViewModel(), KoinComponent {
    private val repository: WeatherRepository by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val dataStore: LauncherDataStore by inject()

    val availableProviders = repository.getAvailableProviders()

    val weatherProvider = repository.selectedProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setWeatherProvider(provider: WeatherSettings.WeatherProvider) {
        repository.selectProvider(provider)
    }

    val imperialUnits = dataStore.data.map { it.weather.imperialUnits }
    fun setImperialUnits(imperialUnits: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setWeather(it.weather.toBuilder().setImperialUnits(imperialUnits))
                    .build()
            }
        }
    }

    val autoLocation = repository.autoLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setAutoLocation(autoLocation: Boolean) {
        repository.setAutoLocation(autoLocation)
    }

    val location = mutableStateOf<WeatherLocation?>(null)

    val hasLocationPermission = permissionsManager.hasPermission(PermissionGroup.Location)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun requestLocationPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Location)
    }


    init {
        viewModelScope.launch {
            val autoLocation = repository.autoLocation
            val location = repository.location
            val lastLocation = repository.lastLocation
            combine(autoLocation, lastLocation, location) { autoLoc, lastLoc, loc ->
                if (autoLoc) lastLoc
                else loc
            }.collectLatest {
                this@WeatherIntegrationSettingsScreenVM.location.value = it
            }
        }
    }

    fun clearWeatherData() {
        repository.clearForecasts()
    }

}