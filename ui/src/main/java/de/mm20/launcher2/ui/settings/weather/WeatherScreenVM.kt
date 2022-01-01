package de.mm20.launcher2.ui.settings.weather

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.WeatherSettings
import de.mm20.launcher2.weather.WeatherLocation
import de.mm20.launcher2.weather.WeatherProvider
import de.mm20.launcher2.weather.brightsky.BrightskyProvider
import de.mm20.launcher2.weather.here.HereProvider
import de.mm20.launcher2.weather.metno.MetNoProvider
import de.mm20.launcher2.weather.openweathermap.OpenWeatherMapProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.coroutineContext

class WeatherScreenVM(private val context: Application) : AndroidViewModel(context), KoinComponent {
    val dataStore: LauncherDataStore by inject()

    val weatherProvider = MutableLiveData<WeatherSettings.WeatherProvider?>(null)
    fun setWeatherProvider(provider: WeatherSettings.WeatherProvider) {
        viewModelScope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setWeather(it.weather.toBuilder().setProvider(provider))
                    .build()
            }
        }
    }

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

    private var provider: WeatherProvider<out WeatherLocation>? = null
        set(value) {
            field = value
            if (value != null) {
                val autoLocation = value.autoLocation
                this.autoLocation.postValue(autoLocation)
                location.postValue(if (autoLocation) value.getLastLocation() else value.getLocation())
            }
        }

    val autoLocation = MutableLiveData(false)
    fun setAutoLocation(autoLocation: Boolean) {
        provider?.autoLocation = autoLocation
        location.postValue(if (autoLocation) provider?.getLastLocation() else provider?.getLocation())
        this.autoLocation.postValue(autoLocation)
    }

    val location = MutableLiveData<WeatherLocation?>(null)
    fun setLocation(location: WeatherLocation) {
        provider?.setLocation(location)
        this.location.postValue(location)
    }

    private var debounceSearchJob : Job? = null
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
                Log.d("MM20", "Searching for $query")
                val provider = provider ?: return@launch
                isSearchingLocation.value = true
                val results = provider
                locationResults.value = results.lookupLocation(query)
                isSearchingLocation.value = false
            }
        }
    }

    val isSearchingLocation = MutableLiveData(false)
    val locationResults = MutableLiveData<List<WeatherLocation>>(emptyList())

    init {
        viewModelScope.launch {
            dataStore.data.map { it.weather.provider }.collectLatest {
                weatherProvider.postValue(it)
                provider = when (it) {
                    WeatherSettings.WeatherProvider.OpenWeatherMap -> OpenWeatherMapProvider(context)
                    WeatherSettings.WeatherProvider.Here -> HereProvider(context)
                    WeatherSettings.WeatherProvider.BrightSky -> BrightskyProvider(context)
                    else -> MetNoProvider(context)
                }
            }
        }
    }

}