package de.mm20.launcher2.weather

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository(application)

    init {
        requestUpdate(application)
    }

    val forecasts: LiveData<List<DailyForecast>> by lazy {
        repository.forecasts
    }

    fun requestUpdate(context: Context) {
        repository.requestUpdate(context)
    }
}