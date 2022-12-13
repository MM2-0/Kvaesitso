package de.mm20.launcher2.ui.common

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.weather.WeatherLocation
import de.mm20.launcher2.weather.WeatherRepository
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.coroutineContext

class WeatherLocationSearchDialogVM: ViewModel(), KoinComponent {
    private val repository: WeatherRepository by inject()

    val isSearchingLocation = MutableLiveData(false)
    val locationResults = MutableLiveData<List<WeatherLocation>>(emptyList())

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

    fun setLocation(location: WeatherLocation) {
        locationResults.postValue(emptyList())
        repository.setAutoLocation(false)
        repository.setLocation(location)
    }
}