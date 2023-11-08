package de.mm20.launcher2.openstreetmaps

import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val openStreetMapsModule = module {
    single<SearchableRepository<OsmLocation>>(named<Location>()) { OsmRepository(androidContext(), get()) }
    factory<SearchableDeserializer>(named(OsmLocation.DOMAIN)) { OsmLocationDeserializer() }
}