package de.mm20.launcher2.publictransport

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import de.mm20.launcher2.preferences.search.PublicTransportSearchSettings
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.PublicTransportStop
import de.mm20.launcher2.search.QueryableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

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

            if (providers.isEmpty()) {
                send(persistentListOf())
                return@collectLatest
            }

            val results = mutableListOf<PublicTransportStop>()
            for (prov in providers) {
                results.addAll(prov.search(query, allowNetwork))
            }
            send(results.toImmutableList())
        }
    }
}