/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.search.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchCalendarTest {
    @Test
    fun aDayRoundTripsThroughItsEpochDayNumber() {
        listOf(
            SearchDate(1970, 1, 1) to 0L,
            SearchDate(2000, 3, 1) to 11017L,
            SearchDate(2026, 4, 1) to 20544L,
        ).forEach { (date, days) ->
            assertEquals(days, date.daysFromEpoch(), "$date")
            assertEquals(date, SearchDate.civilFromDays(days))
        }
    }

    @Test
    fun februaryKnowsItsOwnLength() {
        assertEquals(29, SearchDate.lastDayOfMonth(2024, 2))
        assertEquals(28, SearchDate.lastDayOfMonth(2026, 2))
        // 1900 is not a leap year, 2000 is: the century rule and its exception.
        assertEquals(28, SearchDate.lastDayOfMonth(1900, 2))
        assertEquals(29, SearchDate.lastDayOfMonth(2000, 2))
    }

    @Test
    fun aDayThatDoesNotExistIsRefusedRatherThanRolledOver() {
        assertNull(SearchDate.parse("2026-02-31"))
        assertNull(SearchDate.parse("2026-13-01"))
        assertNull(SearchDate.parse("2026-4-1"))
        assertNull(SearchDate.parse("06/08/2026"))
        assertEquals(SearchDate(2024, 2, 29), SearchDate.parse("2024-02-29"))
    }

    @Test
    fun aDayWritesItselfBackAsTheIsoSpelling() {
        assertEquals("2026-04-01", SearchDate(2026, 4, 1).ymd())
        assertEquals("0999-01-02", SearchDate(999, 1, 2).ymd())
    }

    @Test
    fun theWeekdayIsCountedFromSunday() {
        // 2026-01-04 is a Sunday, which is what the weekday headings are built from.
        assertEquals(0, SearchDate(2026, 1, 4).dayOfWeek())
        assertEquals(3, SearchDate(2026, 4, 1).dayOfWeek())
    }

    @Test
    fun addingAMonthClampsToTheShorterMonthsLastDay() {
        assertEquals(SearchDate(2026, 2, 28), SearchDate(2026, 1, 31).plusMonths(1))
        assertEquals(SearchDate(2024, 2, 29), SearchDate(2024, 1, 31).plusMonths(1))
        // Stepping across a year boundary in both directions.
        assertEquals(SearchDate(2027, 1, 15), SearchDate(2026, 12, 15).plusMonths(1))
        assertEquals(SearchDate(2025, 12, 15), SearchDate(2026, 1, 15).plusMonths(-1))
    }

    @Test
    fun theGridPutsTheFirstUnderTheRightWeekday() {
        // April 2026 starts on a Wednesday; with a Sunday-first week that is three blanks.
        val grid = SearchCalendar.monthGrid(SearchDate(2026, 4, 1), SearchDate(2026, 4, 15), weekStart = 0, label = "April 2026")
        assertEquals(3, grid.lead)
        assertEquals(30, grid.days.size)
        // With a Monday-first week the same month leads with two.
        assertEquals(2, SearchCalendar.monthGrid(SearchDate(2026, 4, 1), SearchDate(2026, 4, 15), weekStart = 1, label = "").lead)
    }

    @Test
    fun theGridMarksTodayAndTheDaysAfterIt() {
        val grid = SearchCalendar.monthGrid(SearchDate(2026, 4, 1), SearchDate(2026, 4, 15), weekStart = 0, label = "")
        assertEquals(1, grid.days.count { it.isToday })
        assertTrue(grid.days.single { it.isToday }.date == SearchDate(2026, 4, 15))
        assertEquals(15, grid.days.count { it.isAhead })
    }

    @Test
    fun weekdayHeadingsRotateToTheLocalesFirstDay() {
        val names = listOf("S", "M", "T", "W", "T", "F", "S")
        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), SearchCalendar.weekdayHeadings(0, names))
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), SearchCalendar.weekdayHeadings(1, names))
    }

    @Test
    fun aHalfTypedDateNamesAMonthOnlyOnceTheMonthIsWhole() {
        assertNull(SearchCalendar.typedMonth("2026"))
        assertNull(SearchCalendar.typedMonth("2026-0"))
        assertNull(SearchCalendar.typedMonth("2026-13"))
        assertEquals(SearchDate(2026, 4, 1), SearchCalendar.typedMonth("2026-04"))
        assertEquals(SearchDate(2026, 4, 1), SearchCalendar.typedMonth("2026-04-17"))
    }

    @Test
    fun quickPicksResolveToAbsoluteDaysNotOffsets() {
        // A saved search reading `since:7d` would mean a different window every morning.
        val picks = SearchCalendar.quickPicks(DateField.SINCE, SearchDate(2026, 4, 15))
        assertEquals("2026-04-15", picks.first().value)
        assertEquals("2026-04-09", picks[1].value)
        assertTrue(picks.all { SearchDate.parse(it.value) != null })
    }

    @Test
    fun aLocalDayEndsTheSecondBeforeTheNextOneStarts() {
        val day = SearchDate(2026, 4, 1)
        // Never midnight + 86,399: a local day that crosses a clock change is 23 or 25 hours.
        assertEquals(LocalClock.startOfDay(day.plusDays(1)) - 1, LocalClock.endOfDay(day))
    }
}
