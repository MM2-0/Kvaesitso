package de.mm20.launcher2.websites

import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import de.mm20.launcher2.search.Website
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val websitesModule = module {
    single<SearchableRepository<Website>>(named<Website>()) { WebsiteRepository(androidContext(), get()) }
    factory<SearchableDeserializer>(named(WebsiteImpl.Domain)) { WebsiteDeserializer() }
}