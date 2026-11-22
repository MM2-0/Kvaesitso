package de.mm20.launcher2.applock

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appLockModule = module {
    factory<AppLockManager> { AppLockManager(androidContext(), get()) }
}
