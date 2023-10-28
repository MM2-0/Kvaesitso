package de.mm20.launcher2.themes

import de.mm20.launcher2.backup.Backupable
import org.koin.core.qualifier.named
import org.koin.dsl.module

val themesModule = module {
    factory<Backupable>(named<ThemeRepository>()) { ThemeRepository(get(), get()) }
    factory { ThemeRepository(get(), get()) }
}