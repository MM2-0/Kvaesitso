package de.mm20.launcher2.searchactions

import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.searchactions.flow.FlowAliasRuntime
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val searchActionsModule = module {
    single { FlowAliasRuntime(get(), get()) }
    factory<Backupable>(named<SearchActionRepository>()) { get<SearchActionRepository>() }
    single<SearchActionRepository> { SearchActionRepositoryImpl(androidContext(), get()) }
    single<SearchActionService> { SearchActionServiceImpl(androidContext(), get(), TextClassifierImpl(), get()) }
}
