import de.mm20.launcher2.locations.providers.openstreetmaps.parseOpeningSchedule
import de.mm20.launcher2.search.location.OpeningHours
import de.mm20.launcher2.search.location.OpeningSchedule
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Duration
import java.time.LocalDateTime
import java.time.Month

class OpeningHoursTest {

    private infix fun OpeningSchedule?.assertEqualTo(actual: OpeningSchedule?)  {
        Assert.assertEquals(this, actual)
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
        }.toSet()
    ) assertEqualTo parseOpeningSchedule(
        "08:00-19:00"
    )

    @Test
    fun testDayOfWeek() = OpeningSchedule.Hours(
        setOf(
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
        setOf(
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
        setOf(
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
        val expectedNoDecember = setOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(8))
        )
        val expectedDecember = emptySet<OpeningHours>()

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
            setOf(OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(4)))
        val expectedNoDecember =
            setOf(OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(8)))

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
        val expectedInRange = setOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8, 0), Duration.ofHours(4))
        )
        val expectedOutOfRange = setOf(
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
        setOf(
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
        val usualWeek = setOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8,0), Duration.ofHours(8))
        )
        val specialMondayWeek = setOf(
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
    fun testLastNthWeekday() {
        val usualWeek = setOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8,0), Duration.ofHours(8))
        )
        val specialMondayWeek = setOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8,0), Duration.ofHours(4))
        )

        for (week in 1..5) {
            OpeningSchedule.Hours(
                if (week == 5)
                    specialMondayWeek
                else
                    usualWeek
            ) assertEqualTo scheduleAt(
                "Mo 08:00-16:00; Mo[-1] 08:00-12:00",
                dayOfMonth = 1 + (week - 1) * 7
            )
        }
    }

    @Test
    fun testMondayOnDecember() {
        val december = setOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8,0), Duration.ofHours(4)),
            OpeningHours(DayOfWeek.FRIDAY, LocalTime.of(8,0), Duration.ofHours(2))
        )
        val notDecember = setOf(
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

    @Test
    fun testAllTogether() {
        val dec = setOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8,0), Duration.ofHours(8))
        ) + setOf(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY).map {
            OpeningHours(it, LocalTime.of(17,0), Duration.ofHours(8))
        }
        val janMar = setOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(6,0), Duration.ofHours(6))
        ) + setOf(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY).map {
            OpeningHours(it, LocalTime.of(17,0), Duration.ofHours(8))
        }
        val aug = setOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(0,30), Duration.ofMinutes(45))
        ) + listOf(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY).map {
            OpeningHours(it, LocalTime.of(17,0), Duration.ofHours(8))
        }
        val elze = setOf(
            OpeningHours(DayOfWeek.MONDAY, LocalTime.of(8,0), Duration.ofHours(8))
        ) + setOf(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY).map {
            OpeningHours(it, LocalTime.of(17,0), Duration.ofHours(8))
        }

        for (month in Month.entries) {
            OpeningSchedule.Hours(when (month) {
                in Month.JANUARY..Month.MARCH -> janMar
                Month.AUGUST -> aug
                Month.DECEMBER -> dec
                else -> elze
            }) assertEqualTo scheduleAt(
                "Mo 08:00-16:00; We-Sa 17:00-01:00; Jan-Mar Mo 06:00-12:00; Dec Fr off; Aug Mo 00:30-01:15; PH,Su off; \"Holiday until 06.09.2420\"",
                month = month
            )
        }
    }

    @Test
    fun testSunriseSunset() {
        // Not supported, but make sure it doesn't crash
        OpeningSchedule.Hours(emptySet()) assertEqualTo parseOpeningSchedule("sunrise-sunset")
    }

    @Test
    @Ignore("Expected to fail with the current implementation")
    // TODO: Fix parseOpeningSchedule to pass this test
    fun testEvenOddWeeks() {
        OpeningSchedule.Hours(
            setOf(
                OpeningHours(dayOfWeek = DayOfWeek.WEDNESDAY, startTime = LocalTime.of(9, 0), duration = Duration.ofHours(3)),
            )
        ) assertEqualTo parseOpeningSchedule(
            "week 1-53/2 Fr 09:00-12:00; week 2-52/2 We 09:00-12:00",
            LocalDateTime.of(2025, Month.APRIL, 18, 0, 0)
        )

        OpeningSchedule.Hours(
            setOf(
                OpeningHours(dayOfWeek = DayOfWeek.FRIDAY, startTime = LocalTime.of(9, 0), duration = Duration.ofHours(3)),
            )
        ) assertEqualTo parseOpeningSchedule(
            "week 1-53/2 Fr 09:00-12:00; week 2-52/2 We 09:00-12:00",
            LocalDateTime.of(2025, Month.APRIL, 10, 0, 0)
        )
    }

    @Test
    fun regressTest0() {
        OpeningSchedule.Hours(
            setOf(
                OpeningHours(dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(8,30), duration = Duration.ofHours(3) + Duration.ofMinutes(30)),
                OpeningHours(dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(8,30), duration = Duration.ofHours(3) + Duration.ofMinutes(30)),
                OpeningHours(dayOfWeek = DayOfWeek.WEDNESDAY, startTime = LocalTime.of(8,30), duration = Duration.ofHours(3) + Duration.ofMinutes(30)),
                OpeningHours(dayOfWeek = DayOfWeek.THURSDAY, startTime = LocalTime.of(8,30), duration = Duration.ofHours(3) + Duration.ofMinutes(30)),
                OpeningHours(dayOfWeek = DayOfWeek.FRIDAY, startTime = LocalTime.of(8,30), duration = Duration.ofHours(3) + Duration.ofMinutes(30)),

                OpeningHours(dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.of(14,0), duration = Duration.ofHours(2)),
                OpeningHours(dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(14,0), duration = Duration.ofHours(2)),
                OpeningHours(dayOfWeek = DayOfWeek.FRIDAY, startTime = LocalTime.of(14,0), duration = Duration.ofHours(2)),

                OpeningHours(dayOfWeek = DayOfWeek.THURSDAY, startTime = LocalTime.of(17,0), duration = Duration.ofHours(2)),
            )
        ) assertEqualTo parseOpeningSchedule(
            "Mo-Fr 08:30-12:00; Mo,Tu,Fr 14:00-16:00; Th 17:00-19:00"
        )
    }

    @Test
    fun regressTest1() {
        OpeningSchedule.Hours(
            setOf(
                OpeningHours(dayOfWeek = DayOfWeek.TUESDAY, startTime = LocalTime.of(11,45), duration = Duration.ofHours(10) + Duration.ofMinutes(45)),
                OpeningHours(dayOfWeek = DayOfWeek.WEDNESDAY, startTime = LocalTime.of(11,45), duration = Duration.ofHours(10) + Duration.ofMinutes(45)),
                OpeningHours(dayOfWeek = DayOfWeek.THURSDAY, startTime = LocalTime.of(11,45), duration = Duration.ofHours(10) + Duration.ofMinutes(45)),
                OpeningHours(dayOfWeek = DayOfWeek.FRIDAY, startTime = LocalTime.of(11,45), duration = Duration.ofHours(10) + Duration.ofMinutes(45)),
                OpeningHours(dayOfWeek = DayOfWeek.SATURDAY, startTime = LocalTime.of(11,45), duration = Duration.ofHours(10) + Duration.ofMinutes(45)),

                OpeningHours(dayOfWeek = DayOfWeek.SUNDAY, startTime = LocalTime.of(11,30), duration = Duration.ofHours(11)),
                // We don't track public holidays. Otherwise, we'd probably go mad!
            )
        ) assertEqualTo parseOpeningSchedule(
            "Tu-Sa 11:45-22:30; Su 11:30-22:30; PH 11:30-22:30"
        )
    }
}