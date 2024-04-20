package de.mm20.launcher2.publictransport

import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.PublicTransportStop
import de.mm20.launcher2.search.QueryableRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val publicTransportModule = module {
    single<PublicTransportRepository> { PublicTransportRepository(androidContext(), get()) }
    factory<QueryableRepository<List<Location>, PublicTransportStop>>(named<PublicTransportStop>()) { get<PublicTransportRepository>() }
}