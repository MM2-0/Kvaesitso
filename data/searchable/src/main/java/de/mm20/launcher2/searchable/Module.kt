package de.mm20.launcher2.searchable

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val searchableModule = module {
    single<SearchableRepository> { SearchableRepositoryImpl(androidContext(), get(), get()) }
}