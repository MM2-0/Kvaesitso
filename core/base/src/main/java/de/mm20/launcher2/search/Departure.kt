package de.mm20.launcher2.search

import java.time.LocalTime

data class Departure(
    val time: LocalTime,
    val line: String,
    val lastStop: String?,
    val type: LineType?
)

enum class LineType {
    BUS, STREETCAR, SUBWAY, TRAIN
}