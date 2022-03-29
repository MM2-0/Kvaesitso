package de.mm20.launcher2.widgets

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val widgetsModule = module {
    single<WidgetRepository> { WidgetRepositoryImpl(androidContext(), get()) }
}