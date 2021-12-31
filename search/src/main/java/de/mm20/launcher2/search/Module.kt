package de.mm20.launcher2.search

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val searchModule = module {
    single<WebsearchRepository> { WebsearchRepositoryImpl(get()) }
    viewModel { WebsearchViewModel(get()) }
}