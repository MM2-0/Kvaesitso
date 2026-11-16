package de.mm20.launcher2.services.tags

import org.koin.dsl.module

val servicesTagsModule = module {
    single { TagsService(get(), get()) }
}