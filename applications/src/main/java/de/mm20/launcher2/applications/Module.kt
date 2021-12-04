package de.mm20.launcher2.applications

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val applicationsModule = module {
    single { AppRepository(androidContext(), get(), get()) }
    viewModel { AppViewModel(get()) }
}