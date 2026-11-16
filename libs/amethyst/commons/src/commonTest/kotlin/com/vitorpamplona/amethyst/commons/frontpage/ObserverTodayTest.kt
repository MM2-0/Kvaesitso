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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ObserverTodayTest {
    private val hour = 3_600L

    /** 2026-11-12T00:00:00Z. */
    private val nov12Utc = 1_794_441_600L

    private val newYork = CronZone.fixed(-5 * 3_600)
    private val tokyo = CronZone.fixed(9 * 3_600)

    private fun edition(
        day: String,
        printedAt: Long,
    ) = SavedEdition(day = day, code = "C$day", printedAt = printedAt, model = "m")

    /** A device in New York: its kept editions are named on its clock. */
    private val device = ObserverCalendar.of(newYork)

    private fun nyNoon(dayOffset: Int) = nov12Utc + 17 * hour + dayOffset * 24 * hour

    private val nov12 = edition("2026-11-12", nyNoon(0) - 5 * hour)
    private val nov11 = edition("2026-11-11", nyNoon(-1))
    private val nov10 = edition("2026-11-10", nyNoon(-2))

    @Test
    fun todaysPaperIsShown() {
        val today = observerToday(listOf(nov12, nov11, nov10), nyNoon(0), device)
        assertEquals("2026-11-12", today.day)
        assertSame(nov12, today.paper)
        assertEquals(listOf(nov11, nov10), today.past.map { it.edition })
        assertTrue(today.hasEditions)
    }

    @Test
    fun noPaperTodayNeverShowsYesterdays() {
        val today = observerToday(listOf(nov11, nov10), nyNoon(0), device)
        assertNull(today.paper)
        assertEquals(listOf(nov11, nov10), today.past.map { it.edition })
        // The checklist knows there were papers: "To print your Observer".
        assertTrue(today.hasEditions)
    }

    @Test
    fun noEditionsAtAll() {
        val today = observerToday(emptyList(), nyNoon(0), device)
        assertNull(today.paper)
        assertTrue(today.past.isEmpty())
        assertFalse(today.hasEditions)
    }

    @Test
    fun midnightTurnsThePage() {
        val kept = listOf(nov12, nov11)
        // 23:59 in New York on the 12th: the 12th's paper is today's.
        val beforeMidnight = nov12Utc + 29 * hour - 60
        assertSame(nov12, observerToday(kept, beforeMidnight, device).paper)
        // A minute past: the 13th has no paper yet, and the 12th's is a past edition.
        val afterMidnight = beforeMidnight + 120
        val next = observerToday(kept, afterMidnight, device)
        assertEquals("2026-11-13", next.day)
        assertNull(next.paper)
        assertEquals(listOf(nov12, nov11), next.past.map { it.edition })
        assertEquals(listOf("2026-11-12", "2026-11-11"), next.past.map { it.day })
        assertEquals("2026-11-12", next.yesterday)
    }

    @Test
    fun theSchedulesZoneDecidesWhenThereIsOne() {
        // Printed at 20:00 in New York on the 11th: 10:00 on the 12th in Tokyo.
        val evening = edition("2026-11-11", nov12Utc + hour)
        // 11:00 on the 12th in Tokyo, 21:00 on the 11th in New York.
        val now = nov12Utc + 2 * hour
        val inTokyo = observerToday(listOf(evening), now, ObserverCalendar.of(tokyo))
        assertEquals("2026-11-12", inTokyo.day)
        assertSame(evening, inTokyo.paper)
        // On the device's own clock it is still the 11th, and the paper is that day's.
        assertSame(evening, observerToday(listOf(evening), now, device).paper)
        // The next morning in New York is still the 12th in Tokyo: the same paper, not a new day.
        val nyMorning = nov12Utc + 13 * hour
        assertSame(evening, observerToday(listOf(evening), nyMorning, ObserverCalendar.of(tokyo)).paper)
        assertNull(observerToday(listOf(evening), nyMorning, device).paper)
    }

    @Test
    fun theDayIsTheOneTheAlreadyPrintedCheckReads() {
        // Whatever the screen calls today's, the morning print in that zone would skip, and the other way round.
        val zones = listOf(newYork, tokyo, CronZone.UTC)
        val printedAts = (0 until 48).map { nov12Utc - 12 * hour + it * hour }
        for (zone in zones) {
            for (printed in printedAts) {
                val kept = listOf(edition("2026-11-12", printed))
                val occurrence = nov12Utc + 6 * hour
                val shown = observerToday(kept, occurrence, ObserverCalendar.of(zone)).paper != null
                assertEquals(ObserverSite.printedFor(printed, null, occurrence, zone), shown)
            }
        }
    }

    @Test
    fun aReadableScheduleNamesTheZoneElseTheDevicesCalendar() {
        val zones = mapOf("Asia/Tokyo" to tokyo)
        val now = nov12Utc + 2 * hour
        val ready = MorningScheduleView(state = ScheduleState.READY, zone = "Asia/Tokyo")
        assertEquals("2026-11-12", ObserverCalendar.forSchedule(ready, device, zones::get).dayOf(now))
        // Not readable (or not known here, or none at all): the device's clock.
        val unreadable = ready.copy(state = ScheduleState.UNREADABLE)
        assertEquals("2026-11-11", ObserverCalendar.forSchedule(unreadable, device, zones::get).dayOf(now))
        assertEquals("2026-11-11", ObserverCalendar.forSchedule(ready.copy(zone = "Mars/Olympus"), device, zones::get).dayOf(now))
        assertEquals("2026-11-11", ObserverCalendar.forSchedule(MorningScheduleView(), device, zones::get).dayOf(now))
    }

    @Test
    fun anEditionWithNoPrintTimeKeepsItsDay() {
        val fromSite = edition("2026-11-12", printedAt = 0)
        assertSame(fromSite, observerToday(listOf(fromSite), nyNoon(0), device).paper)
        assertNull(observerToday(listOf(fromSite), nyNoon(1), device).paper)
    }

    @Test
    fun aLinkToAnotherDayShowsThatPastEdition() {
        val today = observerToday(listOf(nov12, nov11), nyNoon(0), device)
        assertNull(today.pastEdition(EditionPick("2026-11-12")))
        assertSame(nov11, today.pastEdition(EditionPick("2026-11-11"))?.edition)
        assertSame(nov11, today.pastEdition(EditionPick.of(nov11))?.edition)
        // Today's paper picked from anywhere is today, not a past edition.
        assertNull(today.pastEdition(EditionPick.of(nov12)))
        // A day no longer kept, another paper of that day, or none picked: today.
        assertNull(today.pastEdition(EditionPick("2026-10-01")))
        assertNull(today.pastEdition(EditionPick("2026-11-11", code = "other")))
        assertNull(today.pastEdition(null))
        // Today's "ready" notification, named on the device's calendar, while none is kept yet.
        val none = observerToday(emptyList(), nov12Utc + 2 * hour, ObserverCalendar.of(tokyo))
        assertNull(none.pastEdition(EditionPick("2026-11-11")))
    }

    @Test
    fun aPastEditionNamedWithTheDevicesTodayOpensUnderAnotherZonesSchedule() {
        // A device in Los Angeles names its editions on its own clock; the schedule reads Tokyo's.
        val losAngeles = ObserverCalendar.of(CronZone.fixed(-8 * 3_600))
        // Printed at 06:00 in Los Angeles on the 13th: 23:00 in Tokyo, the 13th there too.
        val morning = edition("2026-11-13", nov12Utc + 38 * hour)
        // 18:00 in Los Angeles, still the 13th on the device; 11:00 on the 14th in Tokyo.
        val now = nov12Utc + 50 * hour
        assertEquals("2026-11-13", losAngeles.dayOf(now))
        val today = observerToday(listOf(morning), now, ObserverCalendar.of(tokyo))
        assertEquals("2026-11-14", today.day)
        assertNull(today.paper)
        assertEquals(listOf(morning), today.past.map { it.edition })
        // Tapped in Past editions, it opens, though its day is the device's today.
        assertSame(morning, today.pastEdition(EditionPick.of(morning))?.edition)
        assertSame(morning, today.pastEdition(EditionPick("2026-11-13"))?.edition)
    }

    @Test
    fun theDayBefore() {
        assertEquals("2026-11-11", dayBefore("2026-11-12"))
        assertEquals("2026-02-28", dayBefore("2026-03-01"))
        assertEquals("2024-02-29", dayBefore("2024-03-01"))
        assertEquals("2025-12-31", dayBefore("2026-01-01"))
        assertNull(dayBefore("yesterday"))
    }

    @Test
    fun daysAreNamedRelativeToToday() {
        val today = "2026-11-12"
        assertEquals(RelativeDay.Today, relativeDay("2026-11-12", today))
        assertEquals(RelativeDay.Yesterday, relativeDay("2026-11-11", today))
        // Within the past week: the weekday (2026-11-10 is a Tuesday).
        assertEquals(RelativeDay.Weekday(2), relativeDay("2026-11-10", today))
        assertEquals(RelativeDay.Weekday(5), relativeDay("2026-11-06", today))
        // A week ago or more, ahead of today, or not a day: the date.
        assertEquals(RelativeDay.Date, relativeDay("2026-11-05", today))
        assertEquals(RelativeDay.Date, relativeDay("2026-11-13", today))
        assertEquals(RelativeDay.Date, relativeDay("soon", today))
        // Across a month and a year.
        assertEquals(RelativeDay.Yesterday, relativeDay("2025-12-31", "2026-01-01"))
    }

    @Test
    fun theClockTurnsTheTitleAtMidnight() {
        val zones = mapOf("Asia/Tokyo" to tokyo)
        val beforeMidnight = nov12Utc - 9 * hour - 60
        val tokyoClock = ObserverNow("Asia/Tokyo", beforeMidnight, ObserverCalendar.forZone("Asia/Tokyo", device, zones::get))
        assertEquals("2026-11-11", tokyoClock.today)
        val after = ObserverNow("Asia/Tokyo", beforeMidnight + 120, ObserverCalendar.forZone("Asia/Tokyo", device, zones::get))
        assertEquals("2026-11-12", after.today)
        assertFalse(tokyoClock == after)
        assertEquals(RelativeDay.Yesterday, relativeDay(tokyoClock.today, after.today))
        // A new minute on the same day is the same clock: nothing shown changes.
        assertEquals(after, ObserverNow("Asia/Tokyo", beforeMidnight + 600, ObserverCalendar.forZone("Asia/Tokyo", device, zones::get)))
    }
}
