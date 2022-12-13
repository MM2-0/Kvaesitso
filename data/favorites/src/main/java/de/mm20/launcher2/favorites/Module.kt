package de.mm20.launcher2.favorites

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val favoritesModule = module {
    single<FavoritesRepository> { FavoritesRepositoryImpl(androidContext(), get()) }
}