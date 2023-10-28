package de.mm20.launcher2.backup

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val backupModule = module {
    single { BackupManager(androidContext(), getAll()) }
}