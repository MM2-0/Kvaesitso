package de.mm20.launcher2.sdk.publictransport

import android.database.MatrixCursor
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugin.config.SearchPluginConfig
import de.mm20.launcher2.plugin.contracts.PublicTransportPluginContract
import de.mm20.launcher2.sdk.base.SearchPluginProvider
import java.time.format.DateTimeFormatter

private val LocalTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

abstract class PublicTransportProvider(
    private val config: SearchPluginConfig,
) : SearchPluginProvider<PublicTransportStop>(config) {
    abstract override suspend fun search(
        query: String,
        allowNetwork: Boolean
    ): List<PublicTransportStop>

    final override fun getPluginType(): PluginType = PluginType.PublicTransport

    override fun createCursor(capacity: Int): MatrixCursor {
        return MatrixCursor(
            arrayOf(
                PublicTransportPluginContract.PublicTransportColumns.StationId,
                PublicTransportPluginContract.PublicTransportColumns.StationName,
                PublicTransportPluginContract.PublicTransportColumns.Provider,
                PublicTransportPluginContract.PublicTransportColumns.Latitude,
                PublicTransportPluginContract.PublicTransportColumns.Longitude,
                PublicTransportPluginContract.PublicTransportColumns.Line,
                PublicTransportPluginContract.PublicTransportColumns.LineType,
                PublicTransportPluginContract.PublicTransportColumns.LastStop,
                PublicTransportPluginContract.PublicTransportColumns.LocalTime
            )
        )
    }

    override fun writeToCursor(cursor: MatrixCursor, item: PublicTransportStop) {
        for (departure in item.departures) {
            cursor.addRow(arrayOf(
                item.meta.id,
                item.meta.name,
                item.meta.provider,
                item.meta.latitude,
                item.meta.longitude,
                departure.line,
                departure.lineType.toString(),
                departure.lastStop,
                departure.localTime.format(LocalTimeFormatter)
            ))
        }
    }
}