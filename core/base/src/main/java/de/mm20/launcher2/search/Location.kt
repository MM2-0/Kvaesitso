package de.mm20.launcher2.search

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

interface Location : SavableSearchable {

    val category: LocationCategory?

    val latitude: Double
    val longitude: Double

    val street: String?
    val houseNumber: String?

    val openingHours: List<OpeningTime>?

    val websiteUrl: String?

    override val preferDetailsOverLaunch: Boolean
        get() = false

    fun toAndroidLocation(): android.location.Location {
        val location = android.location.Location("KvaesitsoLocationProvider")

        location.latitude = latitude
        location.longitude = longitude

        return location
    }
}

enum class LocationCategory {
    RESTAURANT,
    FAST_FOOD,
    BAR,
    CAFE,
    HOTEL,
    SUPERMARKET,
    OTHER
}

data class OpeningTime(val dayOfWeek: DayOfWeek, val startTime: LocalTime, val duration: Duration) {
    val isOpen: Boolean
        get() = LocalDate.now().dayOfWeek == dayOfWeek &&
                LocalTime.now().isAfter(startTime) &&
                LocalTime.now().isBefore(startTime.plus(duration))

    override fun toString(): String = "$dayOfWeek $startTime-${startTime.plus(duration)}"
}
