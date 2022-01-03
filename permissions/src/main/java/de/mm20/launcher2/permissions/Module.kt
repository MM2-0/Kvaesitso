package de.mm20.launcher2.permissions

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val permissionsModule = module {
    single<PermissionsManager> { PermissionsManagerImpl(androidContext()) }
}