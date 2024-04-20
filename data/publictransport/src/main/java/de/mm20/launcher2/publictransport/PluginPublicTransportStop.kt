package de.mm20.launcher2.publictransport

import de.mm20.launcher2.search.Departure
import de.mm20.launcher2.search.Location
import de.mm20.launcher2.search.PublicTransportStop

internal data class PluginPublicTransportStop(
    private val wrapLocation: Location,
    override val provider: String,
) : PublicTransportStop, Location by wrapLocation {
    internal val mutableDepartures = mutableListOf<Departure>()

    override val departures: List<Departure> get() = mutableDepartures
}
