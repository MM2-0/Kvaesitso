package de.mm20.launcher2.ui.settings.breezyweather

import android.content.Intent
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.preferences.weather.WeatherSettings
import de.mm20.launcher2.weather.WeatherRepository
import de.mm20.launcher2.weather.breezy.BreezyWeatherProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BreezyWeatherSettingsScreenVM: ViewModel(), KoinComponent {
    private val appRepository: AppRepository by inject()
    private val weatherSettings: WeatherSettings by inject()

    private val breezyWeatherApp = appRepository.findOne("org.breezyweather", Process.myUserHandle())
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    val isBreezyInstalled = breezyWeatherApp
        .map { it != null }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    val isBreezySetAsWeatherProvider = weatherSettings.providerId
        .map { it == BreezyWeatherProvider.Id }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    fun setBreezyAsWeatherProvider() {
        weatherSettings.setProvider(BreezyWeatherProvider.Id)
    }

    fun launchBreezyApp(activity: AppCompatActivity) {
        viewModelScope.launch {
            breezyWeatherApp.first()?.launch(activity, null)
        }
    }

    fun downloadBreezyApp(activity: AppCompatActivity) {
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                data = "https://github.com/breezy-weather/breezy-weather/blob/main/INSTALL.md".toUri()
            }
        )
    }
}