package de.mm20.launcher2.profiles

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val profilesModule = module {
    single<ProfileManager> { ProfileManager(androidContext(), get()) }
}