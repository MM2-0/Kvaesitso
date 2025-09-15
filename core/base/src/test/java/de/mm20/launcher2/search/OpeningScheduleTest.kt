package de.mm20.launcher2.search

import de.mm20.launcher2.search.location.OpeningHours
import de.mm20.launcher2.search.location.OpeningSchedule
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(Parameterized::class)
class OpeningScheduleTest(val date: LocalDateTime, val expected: Boolean) {


    @Test
    fun isOpen() {
        val openingSchedule = OpeningSchedule.Hours(
            /**
             * Monday: 18:00 - Tue. 01:00
             * Tuesday: 10:00 - 00:00
             * Wednesday: 08:00 - 12:00, 15:00 - 19:00
             * Thursday: 00:00 - Fri. 01:00
             * Friday: closed
             * Saturday: 12:00 - 12:30
             * Sunday: 23:00 - Mon. 01:00
             */
            openingHours = setOf(
                OpeningHours(
                    dayOfWeek = DayOfWeek.MONDAY,
                    startTime = LocalTime.of(18, 0),
                    duration = Duration.ofHours(7),
                ),
                OpeningHours(
                    dayOfWeek = DayOfWeek.TUESDAY,
                    startTime = LocalTime.of(10, 0),
                    duration = Duration.ofHours(14),
                ),
                OpeningHours(
                    dayOfWeek = DayOfWeek.WEDNESDAY,
                    startTime = LocalTime.of(8, 0),
                    duration = Duration.ofHours(4),
                ),
                OpeningHours(
                    dayOfWeek = DayOfWeek.WEDNESDAY,
                    startTime = LocalTime.of(15, 0),
                    duration = Duration.ofHours(4),
                ),
                OpeningHours(
                    dayOfWeek = DayOfWeek.THURSDAY,
                    startTime = LocalTime.of(0, 0),
                    duration = Duration.ofHours(25),
                ),
                OpeningHours(
                    dayOfWeek = DayOfWeek.SATURDAY,
                    startTime = LocalTime.of(12, 0),
                    duration = Duration.ofMinutes(30),
                ),
                OpeningHours(
                    dayOfWeek = DayOfWeek.SUNDAY,
                    startTime = LocalTime.of(23, 0),
                    duration = Duration.ofHours(2),
                ),
            )
        )
        println("${date.dayOfWeek}, ${date.toLocalTime()}")
        Assert.assertEquals(expected, openingSchedule.isOpen(date))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = listOf(
            // Monday, 22:00
            arrayOf(LocalDateTime.of(2024, 5, 13, 22, 0, 0), true),
            // Tuesday, 01:01
            arrayOf(LocalDateTime.of(2024, 5, 14, 1, 1, 0), false),
            // Wednesday, 11:00
            arrayOf(LocalDateTime.of(2024, 5, 15, 11, 0, 0), true),
            // Wednesday, 13:00
            arrayOf(LocalDateTime.of(2024, 5, 15, 13, 0, 0), false),
            // Wednesday, 16:00
            arrayOf(LocalDateTime.of(2024, 5, 15, 16, 0, 0), true),
            // Thursday, 00:00
            arrayOf(LocalDateTime.of(2024, 5, 16, 0, 0, 0), true),
            // Thursday, 02:00
            arrayOf(LocalDateTime.of(2024, 5, 16, 2, 0, 0), true),
            // Friday, 00:30
            arrayOf(LocalDateTime.of(2024, 5, 17, 0, 30, 0), true),
            // Friday, 12:00
            arrayOf(LocalDateTime.of(2024, 5, 17, 12, 0, 0), false),
            // Saturday, 12:15
            arrayOf(LocalDateTime.of(2024, 5, 18, 12, 15, 0), true),
            // Saturday, 12:31
            arrayOf(LocalDateTime.of(2024, 5, 18, 12, 31, 0), false),
            // Sunday, 23:30
            arrayOf(LocalDateTime.of(2024, 5, 19, 23, 30, 0), true),
            // Monday, 00:30
            arrayOf(LocalDateTime.of(2024, 5, 20, 0, 30, 0), true),
        )
    }
}