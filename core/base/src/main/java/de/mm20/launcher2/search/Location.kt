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

    override val preferDetailsOverLaunch: Boolean
        get() = false
}

enum class LocationCategory {
    Restaurant,
    Bar,
    Cafe,
    Hotel,
    Supermarket,
    Other
}

data class OpeningTime(val dayOfWeek: DayOfWeek, val startTime: LocalTime, val duration: Duration) {
    val isOpen: Boolean
        get() = LocalDate.now().dayOfWeek == dayOfWeek &&
                LocalTime.now().isAfter(startTime) &&
                LocalTime.now().isBefore(startTime.plus(duration))
}


