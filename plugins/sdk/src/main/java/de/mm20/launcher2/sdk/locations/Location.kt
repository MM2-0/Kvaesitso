package de.mm20.launcher2.sdk.locations

import android.graphics.Color
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.OpeningSchedule
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
