package de.mm20.launcher2.data.plugins

import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.plugin.PluginRepository
import org.koin.dsl.module

val dataPluginsModule = module {
    factory<PluginRepository> { PluginRepositoryImpl(get<AppDatabase>().pluginDao()) }
}