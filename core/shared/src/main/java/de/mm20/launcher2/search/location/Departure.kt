package de.mm20.launcher2.search.location

import android.graphics.Color
import de.mm20.launcher2.serialization.ColorSerializer
import de.mm20.launcher2.serialization.DurationSerializer
import de.mm20.launcher2.serialization.LocalTimeSerializer
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalTime

@Serializable
data class Departure(
    @Serializable(with = LocalTimeSerializer::class)
    val time: LocalTime,
    @Serializable(with = DurationSerializer::class)
    val delay: Duration?,
    val line: String,
    val lastStop: String?,
    val type: LineType?,
    @Serializable(with = ColorSerializer::class)
    val lineColor: Color?,
)

enum class LineType {
    BUS, STREETCAR, SUBWAY, TRAIN, FERRY
}