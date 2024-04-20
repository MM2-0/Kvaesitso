package de.mm20.launcher2.search

import java.time.LocalTime

fun LocationCategory?.isPublicTransportStopCategory(): Boolean =
    when (this) {
        LocationCategory.TRAM_STOP,
        LocationCategory.RAILWAY_STOP,
        LocationCategory.BUS_STATION,
        LocationCategory.BUS_STOP -> true

        else -> false
    }


interface PublicTransportStop : Location {
    val departures: List<Departure>
    val provider: String
}

data class Departure(
    val time: LocalTime,
    val line: String,
    val lastStop: String?,
    val type: LineType?
)

enum class LineType {
    BUS, STREETCAR, SUBWAY, TRAIN
}
