package de.mm20.launcher2.websites

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val websitesModule = module {
    single<WebsiteRepository> { WebsiteRepositoryImpl(androidContext()) }
}