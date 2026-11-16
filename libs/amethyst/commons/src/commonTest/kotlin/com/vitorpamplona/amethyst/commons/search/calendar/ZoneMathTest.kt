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

/**
 * The day arithmetic the iOS [LocalClock] runs, on synthetic zones.
 *
 * The point of these is that an Apple source set can be compiled off a Mac but never *run* off
 * one, so anything left inside `LocalClock.ios.kt` is unexercised by construction. Everything
 * except the Foundation lookups it borrows lives in [ZoneMath], and every zone below is a clock
 * change some country has actually observed.
 */
class ZoneMathTest {
    /** The base offset until the first change, then whatever the latest change at or before an instant says. */
    private class TestZone(
        private val base: Long,
        private val changes: List<Pair<Long, Long>> = emptyList(),
    ) : ZoneOffsets {
        override fun at(epochSeconds: Long): Long {
            var offset = base
            changes.forEach { (instant, newOffset) -> if (epochSeconds >= instant) offset = newOffset }
            return offset
        }
    }

    /** +05:45, and no clock change since 1986. */
    private val kathmandu = TestZone(20700L)

    /**
     * America/New_York in 2026: 02:00 EST becomes 03:00 EDT on 03-08 at 07:00Z, and 02:00 EDT
     * becomes 01:00 EST on 11-01 at 06:00Z.
     */
    private val newYork = TestZone(-18000L, listOf(1772953200L to -14400L, 1793512800L to -18000L))

    /**
     * America/Santiago on 2026-09-06: the change lands *on* midnight, so the day starts at 01:00
     * and 2026-09-06T00:00 local is an instant that never happens.
     */
    private val santiago = TestZone(-14400L, listOf(1788667200L to -10800L))

    /**
     * America/Havana on 2026-11-01: 01:00 EDT becomes 00:00 EST at 05:00Z, so local midnight
     * happens twice — once at 04:00Z on the old offset and again at 05:00Z on the new one.
     */
    private val havana = TestZone(-14400L, listOf(1793509200L to -18000L))

    @Test
    fun aZoneThatNeverChangesIsJustAnOffset() {
        val day = SearchDate(2026, 4, 1)
        assertEquals(1775001600L - 20700L, ZoneMath.startOfDay(day, kathmandu))
        assertEquals(1775088000L - 20700L - 1L, ZoneMath.endOfDay(day, kathmandu))
    }

    @Test
    fun theDayTheClocksSpringForwardIsTwentyThreeHoursLong() {
        val day = SearchDate(2026, 3, 8)
        // 05:00Z is 00:00 EST, on the offset still in force before the 07:00Z change.
        assertEquals(1772946000L, ZoneMath.startOfDay(day, newYork))
        // 04:00Z the next morning is already 00:00 EDT, so this day ends one second earlier.
        assertEquals(1773028799L, ZoneMath.endOfDay(day, newYork))
        assertEquals(23 * 3600L, ZoneMath.endOfDay(day, newYork) - ZoneMath.startOfDay(day, newYork) + 1L)
    }

    @Test
    fun theDayTheClocksFallBackIsTwentyFiveHoursLong() {
        val day = SearchDate(2026, 11, 1)
        assertEquals(1793505600L, ZoneMath.startOfDay(day, newYork))
        assertEquals(1793595599L, ZoneMath.endOfDay(day, newYork))
        assertEquals(25 * 3600L, ZoneMath.endOfDay(day, newYork) - ZoneMath.startOfDay(day, newYork) + 1L)
    }

    @Test
    fun aDayWhoseMidnightNeverHappensStartsWhenTheGapEnds() {
        // Santiago skips 2026-09-06T00:00 entirely; the day's first real instant is 01:00 local.
        assertEquals(1788667200L, ZoneMath.startOfDay(SearchDate(2026, 9, 6), santiago))
        // The day before still ends one second earlier, and keeps its own full 24 hours: the
        // hour that went missing came off the *start* of the 6th, which is 23 hours long.
        assertEquals(1788667199L, ZoneMath.endOfDay(SearchDate(2026, 9, 5), santiago))
        assertEquals(86400L, ZoneMath.endOfDay(SearchDate(2026, 9, 5), santiago) - ZoneMath.startOfDay(SearchDate(2026, 9, 5), santiago) + 1L)
        assertEquals(23 * 3600L, ZoneMath.endOfDay(SearchDate(2026, 9, 6), santiago) - ZoneMath.startOfDay(SearchDate(2026, 9, 6), santiago) + 1L)
    }

    @Test
    fun aDayWhoseMidnightHappensTwiceStartsAtTheFirstOne() {
        // A `since:` bound wants the earlier of the two midnights, which is what java.time's
        // atStartOfDay picks as well; the later one would silently drop an hour of notes.
        assertEquals(1793505600L, ZoneMath.startOfDay(SearchDate(2026, 11, 1), havana))
    }

    @Test
    fun anEpochDayCountsLocalDaysAcrossAClockChange() {
        // 2026-03-08 is day 20520. Local midnight in New York is 05:00Z before the change.
        assertEquals(20520L, ZoneMath.epochDayAt(1772946000L, newYork))
        assertEquals(20519L, ZoneMath.epochDayAt(1772945999L, newYork))
        // 23:59:59 EDT on 03-08 is 03:59:59Z the next UTC day: still 03-08 here.
        assertEquals(20520L, ZoneMath.epochDayAt(1773028799L, newYork))
        assertEquals(20521L, ZoneMath.epochDayAt(1773028800L, newYork))
        // Before 1970 the day still floors, not truncates toward zero.
        assertEquals(-1L, ZoneMath.epochDayAt(-1L, TestZone(0L)))
    }

    @Test
    fun anInstantNamesTheCivilDayItFallsOnHere() {
        // One second either side of local midnight in New York.
        assertEquals(SearchDate(2026, 3, 8), ZoneMath.dayAt(1772946000L, newYork))
        assertEquals(SearchDate(2026, 3, 7), ZoneMath.dayAt(1772945999L, newYork))
        // The same instant is already the next day in Kathmandu.
        assertEquals(SearchDate(2026, 3, 8), ZoneMath.dayAt(1772945999L, kathmandu))
    }

    @Test
    fun everyDayOfAChangingYearStartsWhereTheOneBeforeItEnded() {
        // No gap and no overlap between one day's end and the next day's start, across a year
        // holding both transitions — the property a `since:`/`until:` pair depends on.
        var day = SearchDate(2026, 1, 1)
        while (day < SearchDate(2027, 1, 1)) {
            val next = day.plusDays(1)
            assertEquals(ZoneMath.startOfDay(next, newYork), ZoneMath.endOfDay(day, newYork) + 1L, "$day")
            day = next
        }
    }
}
