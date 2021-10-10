package de.mm20.launcher2.wikipedia

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val wikipediaModule = module {
    single { WikipediaRepository(androidContext()) }
    viewModel { WikipediaViewModel(get()) }
}