package de.mm20.launcher2.themes

import org.koin.dsl.module

val themesModule = module {
    factory { ThemeRepository(get(), get()) }
}