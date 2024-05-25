package de.mm20.launcher2.search.location

import android.graphics.Color
import de.mm20.launcher2.serialization.ColorSerializer
import de.mm20.launcher2.serialization.DurationSerializer
import de.mm20.launcher2.serialization.ZonedDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.ZonedDateTime

@Serializable
data class Departure(
    /**
     * The scheduled time of the departure.
     */
    @Serializable(with = ZonedDateTimeSerializer::class)
    val time: ZonedDateTime,
    /**
     * The delay of the departure.
     */
    @Serializable(with = DurationSerializer::class)
    val delay: Duration?,
    /**
     * Name of the line (i.e. "11", "U2", "S1").
     */
    val line: String,
    val lastStop: String?,
    val type: LineType?,
    @Serializable(with = ColorSerializer::class)
    val lineColor: Color?,
)

enum class LineType {
    BUS, STREETCAR, SUBWAY, TRAIN, FERRY
}