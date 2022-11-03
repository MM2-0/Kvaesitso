package de.mm20.launcher2.search

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val searchModule = module {
    single<SearchService> {
        SearchServiceImpl(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
    single<WebsearchRepository> { WebsearchRepositoryImpl(androidContext(), get()) }
}