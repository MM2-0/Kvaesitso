package de.mm20.launcher2.preferences

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val preferencesModule = module {
    single { androidContext().dataStore }
}