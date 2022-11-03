package de.mm20.launcher2.searchactions

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val searchActionsModule = module {
    single<SearchActionRepository> { SearchActionRepositoryImpl() }
    single<SearchActionService> { SearchActionServiceImpl(androidContext(), get(), TextClassifierImpl()) }
}