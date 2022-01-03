package de.mm20.launcher2.weather

import android.content.Context
import android.util.Log
import androidx.datastore.dataStore
import androidx.work.*
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.WeatherSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.util.*
import java.util.concurrent.TimeUnit

interface WeatherRepository {
    val forecasts: Flow<List<DailyForecast>>

    suspend fun lookupLocation(query: String): List<WeatherLocation>

    val lastLocation: Flow<WeatherLocation?>
    val location: Flow<WeatherLocation?>
    val autoLocation: Flow<Boolean>

    fun setLocation(location: WeatherLocation)
    fun setAutoLocation(autoLocation: Boolean)
    fun setLastLocation(lastLocation: WeatherLocation?)

    fun selectProvider(provider: WeatherSettings.WeatherProvider)

    val selectedProvider: Flow<WeatherSettings.WeatherProvider>

    fun clearForecasts()
}

class WeatherRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase,
    private val dataStore: LauncherDataStore,
) : WeatherRepository, KoinComponent {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var provider: WeatherProvider<out WeatherLocation>

    override val selectedProvider = dataStore.data.map { it.weather.provider }

    override val forecasts: Flow<List<DailyForecast>>
        get() = database.weatherDao().getForecasts()
            .map { it.map { Forecast(it) } }
            .map {
                groupForecastsPerDay(it)
            }

    override val lastLocation = MutableStateFlow<WeatherLocation?>(null)
    override val location = MutableStateFlow<WeatherLocation?>(null)
    override val autoLocation = MutableStateFlow(false)

    override fun setLocation(location: WeatherLocation) {
        provider.setLocation(location)
        this.location.value = location
        provider.resetLastUpdate()
        requestUpdate()
    }

    override fun setAutoLocation(autoLocation: Boolean) {
        provider.autoLocation = autoLocation
        this.autoLocation.value = autoLocation
        provider.resetLastUpdate()
        requestUpdate()
    }

    override fun setLastLocation(lastLocation: WeatherLocation?) {
        this.lastLocation.value = lastLocation
    }

    override suspend fun lookupLocation(query: String): List<WeatherLocation> {
        return provider.lookupLocation(query)
    }

    override fun selectProvider(provider: WeatherSettings.WeatherProvider) {
        scope.launch {
            dataStore.updateData {
                it.toBuilder()
                    .setWeather(
                        it.weather.toBuilder()
                            .setProvider(provider)
                    )
                    .build()
            }
        }
    }

    init {
        val weatherRequest =
            PeriodicWorkRequest.Builder(WeatherUpdateWorker::class.java, 60, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "weather",
            ExistingPeriodicWorkPolicy.KEEP, weatherRequest
        )

        provider = runBlocking {
            val selectedProvider = selectedProvider.first()
            get { parametersOf(selectedProvider) }
        }

        scope.launch {
            var providerSetting: WeatherSettings.WeatherProvider? = null
            selectedProvider.collectLatest {
                if (it != providerSetting) {
                    provider = get { parametersOf(it) }
                    location.value = provider.getLocation()
                    lastLocation.value = provider.getLastLocation()
                    autoLocation.value = provider.autoLocation

                    // Force weather data update but only if provider has changed; not during
                    // initialization
                    if (providerSetting != null) {
                        provider.resetLastUpdate()
                        requestUpdate()
                    }
                    providerSetting = it
                }
            }
        }
    }

    private fun groupForecastsPerDay(forecasts: List<Forecast>): List<DailyForecast> {
        val dailyForecasts = mutableListOf<DailyForecast>()
        val calendar = Calendar.getInstance()
        var currentDay = 0
        var currentDayForecasts: MutableList<Forecast> = mutableListOf()
        for (fc in forecasts) {
            calendar.timeInMillis = fc.timestamp
            if (currentDay != calendar.get(Calendar.DAY_OF_YEAR)) {
                if (currentDayForecasts.isNotEmpty()) {
                    dailyForecasts.add(
                        DailyForecast(
                            timestamp = currentDayForecasts.first().timestamp,
                            minTemp = currentDayForecasts.minByOrNull { it.temperature }?.temperature
                                ?: 0.0,
                            maxTemp = currentDayForecasts.maxByOrNull { it.temperature }?.temperature
                                ?: 0.0,
                            hourlyForecasts = currentDayForecasts
                        )
                    )
                    currentDayForecasts = mutableListOf()
                }
                currentDay = calendar.get(Calendar.DAY_OF_YEAR)
            }
            currentDayForecasts.add(fc)
        }
        if (currentDayForecasts.isNotEmpty()) {
            dailyForecasts.add(
                DailyForecast(
                    timestamp = currentDayForecasts.first().timestamp,
                    minTemp = currentDayForecasts.minByOrNull { it.temperature }?.temperature
                        ?: 0.0,
                    maxTemp = currentDayForecasts.maxByOrNull { it.temperature }?.temperature
                        ?: 0.0,
                    hourlyForecasts = currentDayForecasts
                )
            )
        }
        return dailyForecasts
    }


    private fun requestUpdate() {
        val weatherRequest = OneTimeWorkRequest.Builder(WeatherUpdateWorker::class.java)
            .addTag("weather")
            .build()
        WorkManager.getInstance(context).enqueue(weatherRequest)
    }

    override fun clearForecasts() {
        scope.launch {
            withContext(Dispatchers.IO) {
                database.weatherDao().deleteAll()
            }
        }
    }
}

class WeatherUpdateWorker(val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {
        val repository: WeatherRepository by inject()

    override suspend fun doWork(): Result {
        Log.d("MM20", "Requesting weather data")
        val providerPref = repository.selectedProvider.first()
        val provider: WeatherProvider<out WeatherLocation> = get { parametersOf(providerPref) }
        if (!provider.isAvailable()) {
            Log.d("MM20", "Weather provider is not available")
            return Result.failure()
        }
        if (!provider.isUpdateRequired()) {
            Log.d("MM20", "No weather update required")
            return Result.failure()
        }
        val weatherData = provider.fetchNewWeatherData()
        return if (weatherData == null) {
            Log.d("MM20", "Weather update failed")
            Result.retry()
        } else {
            repository.setLastLocation(provider.getLastLocation())
            Log.d("MM20", "Weather update succeeded")
            AppDatabase.getInstance(applicationContext).weatherDao()
                .replaceAll(weatherData.map { it.toDatabaseEntity() })
            Result.success()
        }
    }
}