package de.mm20.launcher2.data.customattrs

import de.mm20.launcher2.backup.Backupable
import org.koin.core.qualifier.named
import org.koin.dsl.module

val customAttrsModule = module {
    factory<Backupable>(named<CustomAttributesRepository>()) { CustomAttributesRepositoryImpl(get(), get()) }
    factory<CustomAttributesRepository> { CustomAttributesRepositoryImpl(get(), get()) }
}