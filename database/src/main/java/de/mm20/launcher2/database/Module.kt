package de.mm20.launcher2.database
import org.koin.dsl.module

val databaseModule = module {
    single { AppDatabase.getInstance(get()) }
}