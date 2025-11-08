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
     * `Duration.ZERO` if the departure is on time,
     * `null` if no real-time data is available.
     */
    @Serializable(with = DurationSerializer::class)
    val delay: Duration?,
    /**
     * Name of the line (i.e. "11", "U2", "S1").
     */
    val line: String,
    val lastStop: String?,
    val type: LineType? = null,
    @Serializable(with = ColorSerializer::class)
    val lineColor: Color?,
)

/**
 * Compares two line names. The line naems are split into parts of numbers or letters, then
 * each segment is compared.
 */
object LineNameComparator : Comparator<String> {

    // Split line into parts, e.g. "11A" -> ["11", "A"], "S1" -> ["S", "1"], "40-X" -> ["40", "X"]
    private val regex = Regex("\\p{L}+|[0-9]+")

    override fun compare(a: String, b: String): Int {
        if (a == b) return 0
        val aParts = regex.findAll(a).toList()
        val bParts = regex.findAll(b).toList()

        for (i in 0 until minOf(aParts.size, bParts.size)) {
            val aPart = aParts[i].value
            val bPart = bParts[i].value

            if (aPart == bPart) continue

            val thisPartNumber = aPart.toIntOrNull()
            val otherPartNumber = bPart.toIntOrNull()

            if (thisPartNumber != null && otherPartNumber != null) {
                // both parts are numbers, compare them as numbers
                return thisPartNumber.compareTo(otherPartNumber)
            }

            // one part is a number, the other is a string. numbers are automatically less than strings
            return aPart.compareTo(bPart)
        }

        // 11 < 11A
        return aParts.size.compareTo(bParts.size)
    }

}


// implicit ordering by ordinal
enum class LineType {
    Subway,
    Tram,
    Bus,
    Boat,
    Monorail,
    CableCar,
    AerialTramway,
    CommuterTrain,
    RegionalTrain,
    Train,
    HighSpeedTrain,
    Airplane,
}