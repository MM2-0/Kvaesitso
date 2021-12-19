package de.mm20.launcher2.weather

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val weatherModule = module {
    single { WeatherRepository(androidContext(), get()) }
}