package de.mm20.launcher2.openstreetmaps

import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.openstreetmaps.settings.LocationSearchSettings
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val openStreetMapsModule = module {
    single<OsmRepository> { OsmRepository(get(), get(), get()) }
    factory<SearchableRepository<Location>>(named<Location>()) { get<OsmRepository>() }
    factory<SearchableDeserializer>(named(OsmLocation.DOMAIN)) { OsmLocationDeserializer(get()) }
    single<LocationSearchSettings> { LocationSearchSettings(androidContext()) }
    factory<Backupable>(named<LocationSearchSettings>()) { get<LocationSearchSettings>() }
}