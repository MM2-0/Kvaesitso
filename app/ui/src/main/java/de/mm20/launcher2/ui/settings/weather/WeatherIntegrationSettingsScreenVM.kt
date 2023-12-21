package de.mm20.launcher2.ui.settings.weather

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.weather.WeatherLocation
import de.mm20.launcher2.weather.WeatherProviderInfo
import de.mm20.launcher2.weather.WeatherRepository
import de.mm20.launcher2.weather.settings.WeatherSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WeatherIntegrationSettingsScreenVM : ViewModel(), KoinComponent {
    private val repository: WeatherRepository by inject()
    private val weatherSettings: WeatherSettings by inject()
    private val permissionsManager: PermissionsManager by inject()
    private val dataStore: LauncherDataStore by inject()

    val availableProviders = repository.getProviders()

    val weatherProvider = weatherSettings.providerId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    fun setWeatherProvider(provider: String) {
        weatherSettings.setProviderId(provider)
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

    val autoLocation = weatherSettings.autoLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    fun setAutoLocation(autoLocation: Boolean) {
        weatherSettings.setAutoLocation(autoLocation)
    }

    val location = weatherSettings.autoLocation.flatMapLatest {
        if (it) {
            repository.getForecasts(limit = 1).map { it.firstOrNull()?.location }
        } else {
            weatherSettings.location.map { it?.name }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val hasLocationPermission = permissionsManager.hasPermission(PermissionGroup.Location)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun requestLocationPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Location)
    }


    fun clearWeatherData() {
        repository.deleteForecasts()
    }

}