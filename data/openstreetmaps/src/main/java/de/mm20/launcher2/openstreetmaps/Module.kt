package de.mm20.launcher2.openstreetmaps

import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val openstreetmapsModule = module {
    single<SearchableRepository<OsmLocation>>(named<OsmLocation>()) { OsmRepository(androidContext()) }
    factory<SearchableDeserializer>(named(OsmLocation.DOMAIN)) { OsmLocationDeserializer() }
}