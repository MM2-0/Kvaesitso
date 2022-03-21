package de.mm20.launcher2.appshortcuts

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appShortcutsModule = module {
    single<AppShortcutRepository> { AppShortcutRepositoryImpl(androidContext(), get(), get(), get()) }
}