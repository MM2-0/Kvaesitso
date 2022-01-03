package de.mm20.launcher2.ui.settings.weather

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.WeatherSettings
import de.mm20.launcher2.weather.WeatherLocation
import de.mm20.launcher2.weather.WeatherRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.coroutineContext

class WeatherScreenVM : ViewModel(), KoinComponent {
    private val repository: WeatherRepository by inject()
    private val dataStore: LauncherDataStore by inject()
    private val permissionsManager: PermissionsManager by inject()

    val imperialUnits = dataStore.data.map { it.weather.imperialUnits }.asLiveData()
    fun setImperialUnits(imperialUnits: Boolean) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setWeather(it.weather.toBuilder().setImperialUnits(imperialUnits))
                    .build()
            }
        }
    }

    val weatherProvider = repository.selectedProvider.asLiveData()
    fun setWeatherProvider(provider: WeatherSettings.WeatherProvider) {
        repository.selectProvider(provider)
    }

    val autoLocation = repository.autoLocation.asLiveData()
    fun setAutoLocation(autoLocation: Boolean) {
        repository.setAutoLocation(autoLocation)
    }

    val location = MutableLiveData<WeatherLocation?>(null)
    fun setLocation(location: WeatherLocation) {
        locationResults.postValue(emptyList())
        repository.setLocation(location)
    }

    private var debounceSearchJob: Job? = null
    suspend fun searchLocation(query: String) {
        debounceSearchJob?.cancelAndJoin()
        if (query.isBlank()) {
            locationResults.value = emptyList()
            isSearchingLocation.value = false
            return
        }
        withContext(coroutineContext) {
            debounceSearchJob = launch {
                delay(1000)
                isSearchingLocation.value = true
                locationResults.value = repository.lookupLocation(query)
                isSearchingLocation.value = false
            }
        }
    }

    val hasLocationPermission = permissionsManager.hasPermission(PermissionGroup.Location).asLiveData()

    fun requestLocationPermission(activity: AppCompatActivity) {
        permissionsManager.requestPermission(activity, PermissionGroup.Location)
    }

    val isSearchingLocation = MutableLiveData(false)
    val locationResults = MutableLiveData<List<WeatherLocation>>(emptyList())

    init {
        viewModelScope.launch {
            val autoLocation = repository.autoLocation
            val location = repository.location
            val lastLocation = repository.lastLocation
            combine(autoLocation, lastLocation, location) { autoLoc, lastLoc, loc ->
                if (autoLoc) lastLoc
                else loc
            }.collectLatest {
                this@WeatherScreenVM.location.postValue(it)
            }
        }
    }

    fun clearWeatherData() {
        repository.clearForecasts()
    }

}