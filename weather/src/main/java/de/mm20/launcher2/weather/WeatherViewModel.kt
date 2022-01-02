package de.mm20.launcher2.weather

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WeatherViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val repository : WeatherRepository by inject()

    init {
        requestUpdate(application)
    }

    fun requestUpdate(context: Context) {
    }
}