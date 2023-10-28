package de.mm20.launcher2.searchable

import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.data.Tag
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val searchableModule = module {
    factory <Backupable>(named<SavableSearchableRepository>()) { SavableSearchableRepositoryImpl(androidContext(), get(), get()) }
    factory <SavableSearchableRepository> { SavableSearchableRepositoryImpl(androidContext(), get(), get()) }
    factory<SearchableDeserializer>(named(Tag.Domain)) { TagDeserializer() }
}