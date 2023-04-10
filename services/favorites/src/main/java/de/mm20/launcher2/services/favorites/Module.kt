package de.mm20.launcher2.services.favorites

import org.koin.dsl.module

val favoritesModule = module {
    factory { FavoritesService(get()) }
}