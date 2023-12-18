package de.mm20.launcher2.plugins

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val servicesPluginsModule = module {
    single<PluginService> { PluginServiceImpl(androidContext(), get()) }
}