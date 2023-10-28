package de.mm20.launcher2.preferences

import de.mm20.launcher2.backup.Backupable
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val preferencesModule = module {
    single { androidContext().dataStore }
    factory<Backupable>(named<LauncherDataStore>()) { LauncherStoreBackupComponent(androidContext(), get()) }
}