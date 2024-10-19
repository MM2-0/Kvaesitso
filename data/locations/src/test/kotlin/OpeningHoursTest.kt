import de.mm20.launcher2.locations.providers.openstreetmaps.parseOpeningSchedule
import de.mm20.launcher2.search.location.OpeningHours
import de.mm20.launcher2.search.location.OpeningSchedule
import org.junit.Assert
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Duration
import java.time.LocalDateTime
import java.time.Month

class OpeningHoursTest {

    private infix fun OpeningSchedule?.assertEqualTo(actual: OpeningSchedule?) = when (this) {
        is OpeningSchedule.TwentyFourSeven -> Assert.assertTrue(actual is OpeningSchedule.TwentyFourSeven)
        is OpeningSchedule.Hours -> {
            actual as OpeningSchedule.Hours
            Assert.assertEquals(openingHours.size, actual.openingHours.size)
            Assert.assertEquals(openingHours.toSet(), actual.openingHours.toSet())
        }

        null -> Assert.assertNull(actual)
    }

    private fun scheduleAt(
        osm: String,
        year: Int = 2020,
        month: Month = Month.JUNE,
        dayOfMonth: Int = 17,
        hour: Int = 9,
        minute: Int = 44
    ): OpeningSchedule? =
        parseOpeningSchedule(osm, LocalDateTime.of(year, month, dayOfMonth, hour, minute))

    @Test
    fun test247() = Assert.assertSame(
        OpeningSchedule.TwentyFourSeven,
        parseOpeningSchedule("24/7")
    )

    @Test
    fun testEveryDaySame() = OpeningSchedule.Hours(
        DayOfWeek.entries.map {
            OpeningHours(
                it, LocalTime.of(8, 0), Duration.ofHours(11)
            )
        }
    ) assertEqualTo parseOpeningSchedule(
        "08:00-19:00"
    )

    @Test
    fun testDayOfWeek() = OpeningSchedule.Hours(
        listOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(17, 0), Duration.ofHours(5)),
            OpeningHours(DayOfWeek.WEDNESDAY, LocalTime.of(17, 0), Duration.ofHours(5)),
            OpeningHours(DayOfWeek.THURSDAY, LocalTime.of(17, 0), Duration.ofHours(5)),
            OpeningHours(DayOfWeek.FRIDAY, LocalTime.of(17, 0), Duration.ofHours(5)),
            OpeningHours(DayOfWeek.SATURDAY, LocalTime.of(17, 0), Duration.ofHours(5)),
        )
    ) assertEqualTo parseOpeningSchedule(
        "Mo 17:00-22:00; We-Sa 17:00-22:00"
    )


    @Test
    fun testMultipleRanges() = OpeningSchedule.Hours(
        listOf(
            OpeningHours(DayOfWeek.TUESDAY, LocalTime.of(11, 0), Duration.ofHours(4)),
            OpeningHours(DayOfWeek.WEDNESDAY, LocalTime.of(11, 0), Duration.ofHours(4)),
            OpeningHours(DayOfWeek.THURSDAY, LocalTime.of(11, 0), Duration.ofHours(4)),
            OpeningHours(DayOfWeek.FRIDAY, LocalTime.of(11, 0), Duration.ofHours(4)),
            OpeningHours(DayOfWeek.TUESDAY, LocalTime.of(17, 0), Duration.ofHours(5)),
            OpeningHours(DayOfWeek.WEDNESDAY, LocalTime.of(17, 0), Duration.ofHours(5)),
            OpeningHours(DayOfWeek.THURSDAY, LocalTime.of(17, 0), Duration.ofHours(5)),
            OpeningHours(DayOfWeek.FRIDAY, LocalTime.of(17, 0), Duration.ofHours(5)),
            OpeningHours(DayOfWeek.SATURDAY, LocalTime.of(17, 0), Duration.ofHours(5)),
            OpeningHours(
                DayOfWeek.SUNDAY,
                LocalTime.of(11, 30),
                Duration.ofHours(10) + Duration.ofMinutes(30)
            ),
        )
    ) assertEqualTo parseOpeningSchedule(
        "Tu-Fr 11:00-15:00, 17:00-22:00; Sa 17:00-22:00; Su 11:30-22:00"
    )


    @Test
    fun testComment() = OpeningSchedule.Hours(
        listOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(11, 0), Duration.ofHours(7)),
            OpeningHours(DayOfWeek.TUESDAY, LocalTime.of(11, 0), Duration.ofHours(7)),
            OpeningHours(DayOfWeek.WEDNESDAY, LocalTime.of(11, 0), Duration.ofHours(7)),
            OpeningHours(DayOfWeek.THURSDAY, LocalTime.of(11, 0), Duration.ofHours(7)),
            OpeningHours(DayOfWeek.FRIDAY, LocalTime.of(11, 0), Duration.ofHours(7)),
            OpeningHours(DayOfWeek.SATURDAY, LocalTime.of(11, 0), Duration.ofHours(7)),
            OpeningHours(DayOfWeek.SUNDAY, LocalTime.of(13, 0), Duration.ofHours(5)),
        )
    ) assertEqualTo parseOpeningSchedule(
        "Mo-Sa 11:00-18:00; Su 13:00-18:00; \"Holiday until 11.02.2022\""
    )

    @Test
    fun testMonthException() {
        val expectedNoDecember = listOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(8))
        )
        val expectedDecember = emptyList<OpeningHours>()

        for (month in Month.entries) {
            OpeningSchedule.Hours(
                if (month == Month.DECEMBER)
                    expectedDecember
                else
                    expectedNoDecember
            ) assertEqualTo scheduleAt(
                "Mo 08:00-16:00; Dec off",
                month = month
            )
        }
    }

    @Test
    fun testMonthWeekdayException() {
        val expectedDecember =
            listOf(OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(4)))
        val expectedNoDecember =
            listOf(OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(8)))

        for (month in Month.entries) {
            OpeningSchedule.Hours(
                if (month == Month.DECEMBER)
                    expectedDecember
                else
                    expectedNoDecember
            ) assertEqualTo scheduleAt(
                "Mo 08:00-16:00; Dec Mo 08:00-12:00",
                month = month
            )
        }
    }

    @Test
    fun testMonthSpanException() {
        val expectedInRange = listOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(4))
        )
        val expectedOutOfRange = listOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(8))
        )

        for (month in Month.entries) {
            OpeningSchedule.Hours(
                if (month in Month.JANUARY..Month.MARCH)
                    expectedInRange
                else
                    expectedOutOfRange
            ) assertEqualTo scheduleAt(
                "Mo 08:00-16:00; Jan-Mar Mo 08:00-12:00",
                month = month
            )
        }
    }

    @Test
    fun testSundayOff() = OpeningSchedule.Hours(
        listOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(11, 0), Duration.ofHours(10)),
            OpeningHours(DayOfWeek.TUESDAY, LocalTime.of(11, 0), Duration.ofHours(10)),
            OpeningHours(DayOfWeek.WEDNESDAY, LocalTime.of(11, 0), Duration.ofHours(10)),
            OpeningHours(DayOfWeek.THURSDAY, LocalTime.of(11, 0), Duration.ofHours(10)),
            OpeningHours(DayOfWeek.FRIDAY, LocalTime.of(11, 0), Duration.ofHours(10)),
            OpeningHours(DayOfWeek.SATURDAY, LocalTime.of(11, 0), Duration.ofHours(10)),
        )
    ) assertEqualTo scheduleAt(
        "Mo-Sa 11:00-21:00; PH,Su off"
    )

    @Test
    fun testNthWeekday() {
        val usualWeek = listOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8,0), Duration.ofHours(8))
        )
        val specialMondayWeek = listOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8,0), Duration.ofHours(4))
        )

        for (week in 1..4) {
            OpeningSchedule.Hours(
                if (week == 2)
                    specialMondayWeek
                else
                    usualWeek
            ) assertEqualTo scheduleAt(
                "Mo 08:00-16:00; Mo[2] 08:00-12:00",
                dayOfMonth = 1 + (week - 1) * 7
            )
        }
    }

    @Test
    fun testMondayOnDecember() {
        val december = listOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8,0), Duration.ofHours(4)),
            OpeningHours(DayOfWeek.FRIDAY, LocalTime.of(8,0), Duration.ofHours(2))
        )
        val notDecember = listOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8,0), Duration.ofHours(8)),
            OpeningHours(DayOfWeek.FRIDAY, LocalTime.of(8,0), Duration.ofHours(2))
        )
        for (month in Month.entries) {
            OpeningSchedule.Hours(
                if (month == Month.DECEMBER)
                    december
                else
                    notDecember
            ) assertEqualTo scheduleAt(
                "Mo 08:00-16:00; Dec Mo 08:00-12:00; Fr 08:00-10:00",
                month = month
            )
        }
    }

    // future work
//    @Test
//    fun testSpecificDaysOfMonth() {
//
//
//        scheduleAt(
//            "Mo-Su 08:00-18:00; Apr 10-15 off; Jun 08:00-14:00; Aug off; Dec 25 off"
//        )
//    }
}