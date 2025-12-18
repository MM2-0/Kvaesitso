package de.mm20.launcher2.appshortcuts

import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appShortcutsModule = module {
    factory<AppShortcutRepository> { AppShortcutRepositoryImpl(androidContext(), get(), get(), get(), get()) }
    factory<SearchableRepository<AppShortcut>>(named<AppShortcut>()) { get<AppShortcutRepository>() }
    factory<SearchableDeserializer>(named(LauncherShortcut.Domain)) { LauncherShortcutDeserializer(androidContext()) }
    factory<SearchableDeserializer>(named(LegacyShortcut.Domain)) { LegacyShortcutDeserializer(androidContext()) }
}