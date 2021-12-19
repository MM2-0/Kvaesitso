package de.mm20.launcher2.music

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val musicModule = module {
    single { MusicRepository(androidContext()) }
}