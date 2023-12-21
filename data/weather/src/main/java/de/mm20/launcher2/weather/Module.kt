package de.mm20.launcher2.weather

import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.weather.brightsky.BrightSkyProvider
import de.mm20.launcher2.weather.here.HereProvider
import de.mm20.launcher2.weather.metno.MetNoProvider
import de.mm20.launcher2.weather.openweathermap.OpenWeatherMapProvider
import de.mm20.launcher2.weather.settings.WeatherSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val weatherModule = module {
    single<WeatherRepository> { WeatherRepositoryImpl(androidContext(), get(), get()) }
    single<WeatherSettings> { WeatherSettings(androidContext()) }
    factory<Backupable>(named<WeatherSettings>()) { get<WeatherSettings>() }
    factory<WeatherProvider> { (providerId: String) ->
        when (providerId) {
            OpenWeatherMapProvider.Id -> OpenWeatherMapProvider(androidContext())
            MetNoProvider.Id -> MetNoProvider(androidContext(), get())
            HereProvider.Id -> HereProvider(androidContext())
            BrightSkyProvider.Id -> BrightSkyProvider(androidContext())
            else -> TODO("Implement plugin provider")
        }
    }
}