package de.mm20.launcher2.weather

import de.mm20.launcher2.weather.brightsky.BrightSkyProvider
import de.mm20.launcher2.weather.here.HereProvider
import de.mm20.launcher2.weather.metno.MetNoProvider
import de.mm20.launcher2.weather.openweathermap.OpenWeatherMapProvider
import de.mm20.launcher2.weather.plugin.PluginWeatherProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val weatherModule = module {
    single<WeatherRepository> { WeatherRepositoryImpl(androidContext(), get(), get(), get()) }
    factory<WeatherProvider> { (providerId: String) ->
        when (providerId) {
            OpenWeatherMapProvider.Id -> OpenWeatherMapProvider(androidContext())
            MetNoProvider.Id -> MetNoProvider(androidContext(), get())
            HereProvider.Id -> HereProvider(androidContext())
            BrightSkyProvider.Id -> BrightSkyProvider(androidContext())
            else -> PluginWeatherProvider(androidContext(), providerId)
        }
    }
}