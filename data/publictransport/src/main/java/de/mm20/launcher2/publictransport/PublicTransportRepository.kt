package de.mm20.launcher2.publictransport

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import de.mm20.launcher2.preferences.search.PublicTransportSearchSettings
import de.mm20.launcher2.search.Departure
import de.mm20.launcher2.search.LineType
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.PublicTransportStop
import de.mm20.launcher2.search.QueryableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalTime

internal class PublicTransportRepository(
    private val context: Context,
    private val settings: PublicTransportSearchSettings,
) : QueryableRepository<List<Location>, PublicTransportStop> {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun search(
        query: List<Location>,
        allowNetwork: Boolean,
    ): Flow<ImmutableList<PublicTransportStop>> = channelFlow {
        if (query.isEmpty()) {
            send(persistentListOf())
            return@channelFlow
        }

        settings.enabledProviders.collectLatest { providerIds ->
            val providers = providerIds.map {
                PluginPublicTransportProvider(context, it)
            }

            if (providers.isEmpty() && !BuildConfig.DEBUG) {
                send(persistentListOf())
                return@collectLatest
            }

            val results = mutableListOf<PublicTransportStop>()
            for (prov in providers) {
                results.addAll(prov.search(query, allowNetwork))
            }
            // add some mock data in debug mode
            if (BuildConfig.DEBUG) {
                query.map {
                    results.add(
                        PluginPublicTransportStop(
                            wrapLocation = it,
                            provider = "MockProvider"
                        ).apply {
                            mutableDepartures.addAll(
                                listOf(
                                    Departure(
                                        line = "B1",
                                        lastStop = "Nirvana",
                                        time = LocalTime.now().plusMinutes(5),
                                        type = LineType.BUS
                                    ),
                                    Departure(
                                        line = "S1",
                                        lastStop = "Heaven",
                                        time = LocalTime.now().plusMinutes(6),
                                        type = LineType.TRAIN
                                    ),
                                    Departure(
                                        line = "U1",
                                        lastStop = "Hell",
                                        time = LocalTime.now().plusMinutes(7),
                                        type = LineType.SUBWAY
                                    )
                                )
                            )
                        }
                    )
                }
            }
            send(results.toImmutableList())
        }
    }
}