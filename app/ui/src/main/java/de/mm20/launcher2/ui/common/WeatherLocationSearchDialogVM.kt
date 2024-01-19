package de.mm20.launcher2.ui.common

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.preferences.weather.WeatherLocation
import de.mm20.launcher2.preferences.weather.WeatherSettings
import de.mm20.launcher2.weather.WeatherRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.coroutineContext

class WeatherLocationSearchDialogVM: ViewModel(), KoinComponent {
    private val weatherSettings: WeatherSettings by inject()
    private val repository: WeatherRepository by inject()

    val isSearchingLocation = mutableStateOf(false)
    val locationResults = mutableStateOf<List<WeatherLocation>>(emptyList())

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
                locationResults.value = repository.searchLocations(query).first()
                isSearchingLocation.value = false
            }
        }
    }

    fun setLocation(location: WeatherLocation) {
        locationResults.value = emptyList()
        weatherSettings.setLocation(location)
    }
}