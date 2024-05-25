package de.mm20.launcher2.sdk.locations

import de.mm20.launcher2.search.location.Address
import de.mm20.launcher2.search.location.Attribution
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LocationCategory
import de.mm20.launcher2.search.location.OpeningSchedule

data class Location(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val fixMeUrl: String?,
    val category: LocationCategory?,
    val address: Address?,
    val openingSchedule: OpeningSchedule?,
    val websiteUrl: String?,
    val phoneNumber: String?,
    val userRating: Float?,
    val departures: List<Departure>?,
    val attribution: Attribution? = null,
)