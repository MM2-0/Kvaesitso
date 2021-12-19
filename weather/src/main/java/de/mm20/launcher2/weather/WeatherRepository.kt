package de.mm20.launcher2.weather

import android.content.Context
import android.util.Log
import androidx.work.*
import de.mm20.launcher2.database.AppDatabase
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.*
import java.util.concurrent.TimeUnit

class WeatherRepository(
    val context: Context,
    val database: AppDatabase,
) {

    val forecasts = database.weatherDao().getForecasts()
        .map { it.map { Forecast(it) } }
        .map {
            groupForecastsPerDay(it)
        }

    init {
        val weatherRequest =
            PeriodicWorkRequest.Builder(WeatherUpdateWorker::class.java, 60, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "weather",
            ExistingPeriodicWorkPolicy.KEEP, weatherRequest
        )
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


    fun requestUpdate(context: Context) {
        val provider = WeatherProvider.getInstance(context) ?: return
        if (provider.isUpdateRequired()) {
            val weatherRequest = OneTimeWorkRequest.Builder(WeatherUpdateWorker::class.java)
                .addTag("weather")
                .build()
            WorkManager.getInstance(context).enqueue(weatherRequest)
        } else {
            Log.d("MM20", "No weather update required")
        }
    }
}

class WeatherUpdateWorker(val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val provider = WeatherProvider.getInstance(context) ?: return Result.failure()
        if (!provider.isAvailable()) return Result.failure()
        if (!provider.isUpdateRequired()) return Result.failure()
        val weatherData = provider.fetchNewWeatherData()
        return if (weatherData == null) {
            Log.d("MM20", "Weather update failed")
            Result.retry()
        } else {
            Log.d("MM20", "Weather update succeeded")
            AppDatabase.getInstance(applicationContext).weatherDao()
                .replaceAll(weatherData.map { it.toDatabaseEntity() })
            Result.success()
        }
    }
}