package de.mm20.launcher2.sdk.locations

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

data class Location(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val fixMeUrl: String?,
    val category: String?,
    val street: String?,
    val houseNumber: String?,
    val openingSchedule: OpeningSchedule?,
    val websiteUrl: String?,
    val phoneNumber: String?,
    val userRating: Float?,
    val departures: List<Departure>?,
)

data class OpeningSchedule(
    val isTwentyFourSeven: Boolean,
    val openingHours: List<OpeningHours>,
)

data class OpeningHours(
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val duration: Duration,
)

data class Departure(
    val time: LocalTime,
    val delay: Duration?,
    val line: String,
    val lastStop: String?,
    val type: LineType?,
)

enum class LineType {
    BUS, STREETCAR, SUBWAY, TRAIN, FERRY
}
