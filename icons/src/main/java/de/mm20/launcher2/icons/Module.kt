package de.mm20.launcher2.icons

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val iconsModule = module {
    single { IconPackManager(androidContext()) }
    single { IconRepository(androidContext(), get(), get(), get()) }
}