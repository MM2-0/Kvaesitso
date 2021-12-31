package de.mm20.launcher2.hiddenitems

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val hiddenItemsModule = module {
    single { HiddenItemsRepository(androidContext(), get()) }
    viewModel { HiddenItemsViewModel(get()) }
}