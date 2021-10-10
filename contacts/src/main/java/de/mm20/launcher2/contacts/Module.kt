package de.mm20.launcher2.contacts

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val contactsModule = module {
    single { ContactRepository(androidContext(), get()) }
    viewModel { ContactViewModel(get()) }
}