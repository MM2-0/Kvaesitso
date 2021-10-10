package de.mm20.launcher2.appsearch

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appSearchModule = module {
    single { AppSearchRepository(androidContext(), get()) }
    viewModel { AppSearchViewModel(get()) }
}