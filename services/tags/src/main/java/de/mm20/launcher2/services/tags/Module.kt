package de.mm20.launcher2.services.tags

import de.mm20.launcher2.services.tags.impl.TagsServiceImpl
import org.koin.dsl.module

val servicesTagsModule = module {
    single<TagsService> { TagsServiceImpl(get(), get()) }
}