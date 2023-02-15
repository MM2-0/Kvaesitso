package de.mm20.launcher2.icons

import de.mm20.launcher2.icons.compat.ThemedIconsCompatManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val iconsModule = module {
    single { IconPackManager(androidContext(), get()) }
    single { IconRepository(androidContext(), get(), get(), get()) }
}