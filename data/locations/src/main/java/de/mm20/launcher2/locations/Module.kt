package de.mm20.launcher2.locations

import de.mm20.launcher2.locations.providers.openstreetmaps.OsmLocation
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

val locationsModule = module {
    single<LocationsRepository> { LocationsRepository(get(), get(), get(), get()) }
    factory<SearchableRepository<Location>>(named<Location>()) { get<LocationsRepository>() }
    factory<SearchableDeserializer>(named(OsmLocation.DOMAIN)) { OsmLocationDeserializer(get()) }
}