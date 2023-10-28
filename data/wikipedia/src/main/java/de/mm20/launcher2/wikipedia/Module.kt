package de.mm20.launcher2.wikipedia

import de.mm20.launcher2.search.Article
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val wikipediaModule = module {
    single<SearchableRepository<Article>>(named<Article>()) { WikipediaRepository(androidContext(), get()) }
    factory<SearchableDeserializer>(named(Wikipedia.Domain)) { WikipediaDeserializer(androidContext()) }
}