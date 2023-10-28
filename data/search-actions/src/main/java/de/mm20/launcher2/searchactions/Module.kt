package de.mm20.launcher2.searchactions

import de.mm20.launcher2.backup.Backupable
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val searchActionsModule = module {
    factory<Backupable>(named<SearchActionRepository>()) { SearchActionRepositoryImpl(androidContext(), get()) }
    factory<SearchActionRepository> { SearchActionRepositoryImpl(androidContext(), get()) }
    single<SearchActionService> { SearchActionServiceImpl(androidContext(), get(), TextClassifierImpl()) }
}