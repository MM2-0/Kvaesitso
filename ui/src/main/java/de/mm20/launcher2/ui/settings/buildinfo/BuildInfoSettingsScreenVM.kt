package de.mm20.launcher2.ui.settings.buildinfo

import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import de.mm20.launcher2.accounts.AccountType
import de.mm20.launcher2.accounts.AccountsRepository
import de.mm20.launcher2.preferences.Settings.WeatherSettings.WeatherProvider
import de.mm20.launcher2.weather.WeatherRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.MessageDigest

class BuildInfoSettingsScreenVM : ViewModel(), KoinComponent {
    private val accountsRepository: AccountsRepository by inject()
    private val weatherRepository: WeatherRepository by inject()

    private val availableWeatherProviders = weatherRepository.getAvailableProviders()

    val buildFeatures = mapOf(
        "Accounts: Google" to accountsRepository.isSupported(AccountType.Google),
        "Accounts: Microsoft" to accountsRepository.isSupported(AccountType.Microsoft),
        "Weather providers: HERE" to availableWeatherProviders.contains(WeatherProvider.Here),
        "Weather providers: Met No" to availableWeatherProviders.contains(WeatherProvider.MetNo),
        "Weather providers: OpenWeatherMap" to availableWeatherProviders.contains(WeatherProvider.OpenWeatherMap),
        "Weather providers: BrightSky" to availableWeatherProviders.contains(WeatherProvider.BrightSky),
    )
}