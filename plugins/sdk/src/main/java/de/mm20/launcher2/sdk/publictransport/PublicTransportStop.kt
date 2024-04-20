package de.mm20.launcher2.sdk.publictransport

import java.time.LocalTime

data class PublicTransportStop(
    val meta: Metadata,
    val departures: List<Departure>
)

data class Metadata(
    val id: String,
    val name: String?,
    val provider: String,
    val latitude: Double?,
    val longitude: Double?,
)

data class Departure(
    val line: String,
    val lineType: LineType?,
    val lastStop: String?,
    val localTime: LocalTime,
)

enum class LineType {
    BUS, STREETCAR, SUBWAY, TRAIN
}
