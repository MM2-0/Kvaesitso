package de.mm20.launcher2.sdk.locations

import de.mm20.launcher2.search.location.Address
import de.mm20.launcher2.search.location.Attribution
import de.mm20.launcher2.search.location.Departure
import de.mm20.launcher2.search.location.LocationIcon
import de.mm20.launcher2.search.location.OpeningSchedule

data class Location(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val icon: LocationIcon? = null,
    val category: String?,
    val address: Address? = null,
    val openingSchedule: OpeningSchedule? = null,
    val websiteUrl: String? = null,
    val phoneNumber: String? = null,
    val userRating: Float? = null,
    val departures: List<Departure>? = null,
    val fixMeUrl: String? = null,
    val attribution: Attribution? = null,
)