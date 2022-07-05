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
import kotlin.math.max
import kotlin.math.min

class WeatherWidgetWM : ViewModel(), KoinComponent {
    private val weatherRepository: WeatherRepository by inject()

    private val permissionsManager: PermissionsManager by inject()

    private val dataStore: LauncherDataStore by inject()

    /**
     * Index of the currently selected day in [dailyForecasts]
     */
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

    /**
     * Index of the currently selected forecast in [currentDayForecasts]
     */
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

    /**
     * All available forecasts, grouped by day
     */
    private var forecasts: List<DailyForecast> = emptyList()
    set(value) {
        field = value
        selectedDayIndex = 0
        selectedForecastIndex = 0
        dailyForecasts.postValue(value)
    }

    /**
     * Currently selected forecast, one of [currentDayForecasts]
     */
    val currentForecast = MutableLiveData<Forecast?>(getCurrentlySelectedForecast())

    /**
     * List of forecast summaries for each day
     */
    val dailyForecasts = MutableLiveData<List<DailyForecast>>(emptyList())

    /**
     * Forecasts of the currently selected day (hourly in most cases).
     * This is [DailyForecast.hourlyForecasts] of [currentDailyForecast]
     */
    val currentDayForecasts = MutableLiveData<List<Forecast>>(emptyList())

    /**
     * Daily forecast summary for the currently selected day, one of [dailyForecasts] or null
     */
    val currentDailyForecast = MutableLiveData<DailyForecast>(null)

    init {
        viewModelScope.launch {
            forecastsFlow.collectLatest {
                forecasts = it
                selectNow()
            }
        }
    }

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

    fun selectNow() {
        if (forecasts.isEmpty()) return
        val now = System.currentTimeMillis()
        val dayIndex = max(0, forecasts.indexOfLast { it.timestamp < now })
        val day = forecasts[dayIndex]
        val forecastIndex = max(0, day.hourlyForecasts.indexOfLast { it.timestamp < now })
        selectDay(dayIndex)
        selectForecast(forecastIndex)
    }
}