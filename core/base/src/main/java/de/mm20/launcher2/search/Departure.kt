package de.mm20.launcher2.search

import android.graphics.Color
import java.time.Duration
import java.time.LocalTime

data class Departure(
    val time: LocalTime,
    val delay: Duration?,
    val line: String,
    val lastStop: String?,
    val type: LineType?,
    val lineColor: Color?,
)

enum class LineType {
    BUS, STREETCAR, SUBWAY, TRAIN, FERRY
}