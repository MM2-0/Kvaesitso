package de.mm20.launcher2.search.location

import de.mm20.launcher2.serialization.DurationSerializer
import de.mm20.launcher2.serialization.LocalTimeSerializer
import de.mm20.launcher2.serialization.OpeningScheduleSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

@Serializable
data class OpeningHours(
    @JsonNames("day")
    val dayOfWeek: DayOfWeek,
    @JsonNames("openingTime")
    @Serializable(with = LocalTimeSerializer::class)
    val startTime: LocalTime,
    @Serializable(with = DurationSerializer::class)
    val duration: Duration
) {
    override fun toString(): String = "$dayOfWeek $startTime-${startTime.plus(duration)}"
}

@Serializable(with = OpeningScheduleSerializer::class)
sealed interface OpeningSchedule {
    data object TwentyFourSeven : OpeningSchedule
    @Serializable
    data class Hours(@Serializable val openingHours: List<OpeningHours>) : OpeningSchedule
}