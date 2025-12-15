package de.mm20.launcher2.applications

import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SearchableRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val applicationsModule = module {
    factory<SearchableRepository<Application>>(named<Application>()) { get<AppRepository>() }
    single<AppRepository> { AppRepositoryImpl(androidContext(), get(), get()) }
    factory<SearchableDeserializer>(named(LauncherApp.Domain)) { LauncherAppDeserializer(androidContext()) }
}