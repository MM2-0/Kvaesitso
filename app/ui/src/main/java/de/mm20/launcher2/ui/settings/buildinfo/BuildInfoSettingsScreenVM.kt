package de.mm20.launcher2.ui.settings.buildinfo

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.weather.WeatherRepository
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BuildInfoSettingsScreenVM : ViewModel(), KoinComponent {
    private val weatherRepository: WeatherRepository by inject()

    private val availableWeatherProviders = weatherRepository.getProviders()

    val buildFeatures = availableWeatherProviders.map {
        mapOf(
            "Weather providers: Met No" to it.any { it.id == "metno" },
            "Weather providers: OpenWeatherMap" to it.any { it.id == "owm" },
        )
    }
}