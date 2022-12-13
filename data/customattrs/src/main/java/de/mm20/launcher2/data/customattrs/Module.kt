package de.mm20.launcher2.data.customattrs

import org.koin.dsl.module

val customAttrsModule = module {
    single<CustomAttributesRepository> { CustomAttributesRepositoryImpl(get(), get()) }
}