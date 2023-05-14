package de.mm20.launcher2.ui.settings.buildinfo

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.accounts.AccountType
import de.mm20.launcher2.accounts.AccountsRepository
import de.mm20.launcher2.preferences.Settings.WeatherSettings.WeatherProvider
import de.mm20.launcher2.weather.WeatherRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BuildInfoSettingsScreenVM : ViewModel(), KoinComponent {
    private val accountsRepository: AccountsRepository by inject()
    private val weatherRepository: WeatherRepository by inject()

    private val availableWeatherProviders = weatherRepository.getAvailableProviders()

    val buildFeatures = mapOf(
        "Accounts: Google" to accountsRepository.isSupported(AccountType.Google),
        "Weather providers: HERE" to availableWeatherProviders.contains(WeatherProvider.Here),
        "Weather providers: Met No" to availableWeatherProviders.contains(WeatherProvider.MetNo),
        "Weather providers: OpenWeatherMap" to availableWeatherProviders.contains(WeatherProvider.OpenWeatherMap),
    )
}