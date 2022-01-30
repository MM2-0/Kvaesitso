package de.mm20.launcher2.ui.launcher.widgets.weather

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.weather.DailyForecast
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.WeatherRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.min

class WeatherWidgetWM : ViewModel(), KoinComponent {
    private val weatherRepository: WeatherRepository by inject()

    private val permissionsManager: PermissionsManager by inject()

    private val dataStore: LauncherDataStore by inject()

    private var selectedDayIndex = 0
        set(value) {
            field = min(value, forecasts.lastIndex)
            if (field < 0) {
                currentForecast.postValue(null)
                return
            }
            selectedForecastIndex = min(
                selectedForecastIndex,
                forecasts[value].hourlyForecasts.lastIndex
            )
            currentDayForecasts.postValue(forecasts[value].hourlyForecasts)
            currentDailyForecast.postValue(forecasts[value])
            currentForecast.postValue(getCurrentlySelectedForecast())
        }

    private var selectedForecastIndex = 0
    set(value) {
        if (selectedDayIndex < 0)  {
            currentForecast.postValue(null)
            return
        }
        field = min(value, forecasts[selectedDayIndex].hourlyForecasts.lastIndex)
        currentForecast.postValue(getCurrentlySelectedForecast())
    }

    private val forecastsFlow = weatherRepository.forecasts

    private var forecasts: List<DailyForecast> = emptyList()
    set(value) {
        field = value
        selectedDayIndex = 0
        selectedForecastIndex = 0
        dailyForecasts.postValue(value)
    }

    init {
        viewModelScope.launch {
            forecastsFlow.collectLatest {
                forecasts = it
            }
        }
    }

    val currentForecast = MutableLiveData<Forecast?>(getCurrentlySelectedForecast())
    val dailyForecasts = MutableLiveData<List<DailyForecast>>(emptyList())
    val currentDayForecasts = MutableLiveData<List<Forecast>>(emptyList())
    val currentDailyForecast = MutableLiveData<DailyForecast>(null)

    val hasLocationPermission = permissionsManager.hasPermission(PermissionGroup.Location).asLiveData()
    fun requestLocationPermission(context: AppCompatActivity) {
        permissionsManager.requestPermission(context, PermissionGroup.Location)
    }
    val autoLocation = weatherRepository.autoLocation.asLiveData()

    val imperialUnits = dataStore.data.map { it.weather.imperialUnits }.asLiveData()

    fun selectDay(index: Int) {
        selectedDayIndex = min(index, forecasts.lastIndex)
    }

    fun selectForecast(index: Int) {
        selectedForecastIndex = index
    }

    private fun getCurrentlySelectedForecast(): Forecast? {
        return forecasts.getOrNull(selectedDayIndex)?.hourlyForecasts?.getOrNull(selectedForecastIndex)
    }
}