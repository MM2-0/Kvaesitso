package de.mm20.launcher2.settings

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val settingsModule = module {
    single<SettingsRepository> { SettingsRepositoryImpl(androidContext(), get()) }
}