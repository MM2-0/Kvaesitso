package de.mm20.launcher2.searchable

import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.Tag
import org.koin.core.qualifier.named
import org.koin.dsl.module

val searchableModule = module {
    factory <Backupable>(named<SavableSearchableRepository>()) { SavableSearchableRepositoryImpl(
        get(),
        get()
    ) }
    factory <SavableSearchableRepository> { SavableSearchableRepositoryImpl(get(), get()) }
}