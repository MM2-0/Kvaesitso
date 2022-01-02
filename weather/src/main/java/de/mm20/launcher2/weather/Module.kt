package de.mm20.launcher2.weather

import de.mm20.launcher2.preferences.Settings.WeatherSettings
import de.mm20.launcher2.weather.brightsky.BrightskyProvider
import de.mm20.launcher2.weather.here.HereProvider
import de.mm20.launcher2.weather.metno.MetNoProvider
import de.mm20.launcher2.weather.openweathermap.OpenWeatherMapProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val weatherModule = module {
    single<WeatherRepository> { WeatherRepositoryImpl(androidContext(), get(), get()) }
    factory { (selectedProvider: WeatherSettings.WeatherProvider) ->
        when (selectedProvider) {
            WeatherSettings.WeatherProvider.OpenWeatherMap -> OpenWeatherMapProvider(androidContext())
            WeatherSettings.WeatherProvider.Here -> HereProvider(androidContext())
            WeatherSettings.WeatherProvider.BrightSky -> BrightskyProvider(androidContext())
            else -> MetNoProvider(androidContext())
        }
    }
}