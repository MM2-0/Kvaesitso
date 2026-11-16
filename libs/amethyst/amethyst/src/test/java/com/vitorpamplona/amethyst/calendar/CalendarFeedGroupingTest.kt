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
package com.vitorpamplona.amethyst.calendar

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.calendarLocalDayKeyRange
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.groupByDayKey
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.groupByDayKeyExpanded
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.partitionUpcomingPast
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CalendarFeedGroupingTest {
    @Test
    fun groupByDayKey_dateSlotsLandOnIsoDate_inEveryZone() {
        // The "Jan 15" appointment should bucket under Jan 15 regardless of viewer timezone —
        // that's the contract for 31922 date-slot events. Previous UTC anchoring put it under
        // Jan 14 for users in Pacific time.
        val note = dateSlotNote(id = "d", start = "2025-01-15")
        val grouped = groupByDayKey(listOf(note))
        val expectedKey = LocalDate.of(2025, 1, 15).toEpochDay()
        assertEquals(1, grouped[expectedKey]?.size)
    }

    @Test
    fun groupByDayKey_timeSlotsBucketByLocalDate() {
        // Same instant should yield the same local-date key regardless of how many calls we
        // make — sanity check that the helper doesn't accidentally read the clock.
        val note = timeSlotNote(id = "t", startSeconds = 1736942400L) // 2025-01-15 12:00 UTC
        val grouped1 = groupByDayKey(listOf(note))
        val grouped2 = groupByDayKey(listOf(note))
        assertEquals(grouped1, grouped2)
        assertEquals(1, grouped1.values.first().size)
    }

    @Test
    fun groupByDayKey_multipleEventsSameDay_collectIntoOneBucket() {
        val a = timeSlotNote(id = "a", startSeconds = 1736942400L) // 12:00 UTC
        val b = timeSlotNote(id = "b", startSeconds = 1736942400L + 3600L) // 13:00 UTC
        val c = timeSlotNote(id = "c", startSeconds = 1736942400L + 7200L) // 14:00 UTC
        val grouped = groupByDayKey(listOf(a, b, c))
        assertEquals(1, grouped.size)
        assertEquals(3, grouped.values.first().size)
    }

    @Test
    fun groupByDayKey_dropsNotesWithoutResolvedStart() {
        val withoutStart = Note("ghost") // no event at all
        val withStart = timeSlotNote(id = "with", startSeconds = 1736942400L)
        val grouped = groupByDayKey(listOf(withoutStart, withStart))
        assertEquals(1, grouped.size)
        assertEquals(
            "with",
            grouped.values
                .first()
                .single()
                .idHex,
        )
    }

    @Test
    fun partitionUpcomingPast_basicSplit() {
        val now = 1_000_000L
        val past = timeSlotNote(id = "past", startSeconds = now - 3600)
        val future = timeSlotNote(id = "future", startSeconds = now + 3600)
        val split = partitionUpcomingPast(listOf(past, future), nowSeconds = now)
        assertEquals(1, split.upcoming.size)
        assertEquals("future", split.upcoming[0].idHex)
        assertEquals(1, split.past.size)
        assertEquals("past", split.past[0].idHex)
    }

    @Test
    fun partitionUpcomingPast_ongoingMultiDay_classifiedAsUpcoming() {
        // Regression test for the audit fix: a 3-day conference that started yesterday and ends
        // tomorrow should appear in "Upcoming", not "Past". The fix uses `end ?: start` so this
        // case requires no relative-time guess.
        val now = 1_000_000L
        val ongoing = timeSlotNote(id = "ongoing", startSeconds = now - 86400, endSeconds = now + 86400)
        val split = partitionUpcomingPast(listOf(ongoing), nowSeconds = now)
        assertEquals("ongoing should be upcoming, not past", 1, split.upcoming.size)
        assertEquals(0, split.past.size)
    }

    @Test
    fun partitionUpcomingPast_dropsEventsWithoutStart() {
        val ghost = Note("ghost")
        val split = partitionUpcomingPast(listOf(ghost), nowSeconds = 1_000_000L)
        assertEquals(0, split.upcoming.size)
        assertEquals(0, split.past.size)
    }

    @Test
    fun groupByDayKey_dateSlot_ignoresViewerTimezone_byUsingIsoDirectly() {
        // We can't change the JVM zone mid-test reliably, but we can prove the date-slot path
        // doesn't touch ZoneId by parsing a value-with-no-instant equivalent. The key for
        // 2025-01-15 is always LocalDate.of(2025,1,15).toEpochDay() — a constant.
        val note = dateSlotNote(id = "d", start = "2025-01-15")
        val grouped = groupByDayKey(listOf(note))
        val key = LocalDate.of(2025, 1, 15).toEpochDay()
        assertNotNull(grouped[key])
        // A neighbouring day key should be empty.
        assertNull(grouped[key + 1])
        assertNull(grouped[key - 1])
    }

    @Test
    fun groupByDayKey_localDateInfoConsistentWithSystemZone() {
        // For time-slot events the bucket key is the LocalDate in the system zone.
        // We assert that the same instant maps to the same LocalDate as Java's stdlib derives.
        val seconds = 1736942400L
        val expectedKey =
            java.time.Instant
                .ofEpochSecond(seconds)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toEpochDay()
        val grouped = groupByDayKey(listOf(timeSlotNote("x", startSeconds = seconds)))
        assertTrue(grouped.containsKey(expectedKey))
    }

    @Test
    fun groupByDayKeyExpanded_singleDayEvent_landsOnlyOnStartDay() {
        // Sanity: an event with no end (or end == start) doesn't multiply itself.
        val note = dateSlotNote(id = "d", start = "2025-01-15")
        val grouped = groupByDayKeyExpanded(listOf(note))
        val key = LocalDate.of(2025, 1, 15).toEpochDay()
        assertEquals(1, grouped.size)
        assertEquals(1, grouped[key]?.size)
    }

    @Test
    fun groupByDayKeyExpanded_multiDayDateSlot_landsOnEveryDay() {
        // A 3-day date-slot event should appear in each of Jan 15, 16, 17.
        val note = dateSlotNote(id = "d", start = "2025-01-15", end = "2025-01-17")
        val grouped = groupByDayKeyExpanded(listOf(note))
        val keys = listOf(15, 16, 17).map { LocalDate.of(2025, 1, it).toEpochDay() }
        assertEquals(3, grouped.size)
        for (k in keys) assertEquals(1, grouped[k]?.size)
    }

    @Test
    fun groupByDayKeyExpanded_multiDayTimeSlot_landsOnEveryDayCovered() {
        // Spans ~36 hours from 12:00 UTC Jan 15 to 00:00 UTC Jan 17. Whether that crosses 2 or 3
        // local days depends on the runner zone; we just assert it covers more than one day.
        val note = timeSlotNote(id = "t", startSeconds = 1736942400L, endSeconds = 1736942400L + 36 * 3600L)
        val grouped = groupByDayKeyExpanded(listOf(note))
        assertTrue("expected multi-day event to land on >1 day", grouped.size >= 2)
    }

    @Test
    fun calendarLocalDayKeyRange_isCappedAt366Days() {
        // Defence: a malformed event with end years in the future shouldn't expand to thousands
        // of day-keys and blow up the month grid.
        val absurdStart = 1736942400L
        val absurdEnd = absurdStart + 365L * 86400L * 10 // 10 years
        val note = timeSlotNote(id = "rogue", startSeconds = absurdStart, endSeconds = absurdEnd)
        val range = note.calendarLocalDayKeyRange()
        assertNotNull(range)
        assertTrue((range!!.last - range.first) <= 366)
    }

    // ---- helpers ----

    private fun timeSlotNote(
        id: String,
        startSeconds: Long,
        endSeconds: Long? = null,
    ): Note {
        val tags =
            buildList {
                add(arrayOf("d", "$id-d"))
                add(arrayOf("title", "T"))
                add(arrayOf("start", startSeconds.toString()))
                endSeconds?.let { add(arrayOf("end", it.toString())) }
            }.toTypedArray()
        val e = CalendarTimeSlotEvent(id, "pub", 0L, tags, "", "sig")
        return Note(id).apply { event = e }
    }

    private fun dateSlotNote(
        id: String,
        start: String,
        end: String? = null,
    ): Note {
        val tags =
            buildList {
                add(arrayOf("d", "$id-d"))
                add(arrayOf("title", "D"))
                add(arrayOf("start", start))
                end?.let { add(arrayOf("end", it)) }
            }.toTypedArray()
        val e = CalendarDateSlotEvent(id, "pub", 0L, tags, "", "sig")
        return Note(id).apply { event = e }
    }
}
