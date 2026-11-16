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
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.appointmentView
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.calendarEndSeconds
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.calendarLocalDayKey
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.calendarStartSeconds
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.parseIsoDateToUnixSeconds
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.upcomingFirstCalendarOrder
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CalendarSortKeysTest {
    // 2025-01-15 12:00:00 UTC
    private val sampleEpochSeconds = 1736942400L
    private val sampleEpochSecondsPlus2h = sampleEpochSeconds + 7200L

    @Test
    fun parseIsoDateToUnixSeconds_validDate_anchorsAtLocalMidnight() {
        val parsed = parseIsoDateToUnixSeconds("2025-01-15")
        assertEquals(
            "Should equal local midnight of Jan 15",
            LocalDate.of(2025, 1, 15).atStartOfDay(ZoneId.systemDefault()).toEpochSecond(),
            parsed,
        )
    }

    @Test
    fun parseIsoDateToUnixSeconds_blankOrNull_returnsNull() {
        assertNull(parseIsoDateToUnixSeconds(null))
        assertNull(parseIsoDateToUnixSeconds(""))
        assertNull(parseIsoDateToUnixSeconds("   "))
    }

    @Test
    fun parseIsoDateToUnixSeconds_invalidFormat_returnsNull() {
        assertNull(parseIsoDateToUnixSeconds("01/15/2025"))
        assertNull(parseIsoDateToUnixSeconds("2025-13-01")) // month 13
        assertNull(parseIsoDateToUnixSeconds("garbage"))
    }

    @Test
    fun calendarStartSeconds_timeSlot_returnsEventStart() {
        val note = noteWithTimeSlot(start = sampleEpochSeconds, end = sampleEpochSecondsPlus2h)
        assertEquals(sampleEpochSeconds, note.calendarStartSeconds())
        assertEquals(sampleEpochSecondsPlus2h, note.calendarEndSeconds())
    }

    @Test
    fun calendarStartSeconds_dateSlot_anchorsLocalMidnight() {
        val note = noteWithDateSlot(start = "2025-01-15", end = "2025-01-17")
        val expected = LocalDate.of(2025, 1, 15).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        assertEquals(expected, note.calendarStartSeconds())
    }

    @Test
    fun calendarLocalDayKey_timeSlot_usesLocalCalendarDate() {
        val note = noteWithTimeSlot(start = sampleEpochSeconds)
        val expected =
            java.time.Instant
                .ofEpochSecond(sampleEpochSeconds)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toEpochDay()
        assertEquals(expected, note.calendarLocalDayKey())
    }

    @Test
    fun calendarLocalDayKey_dateSlot_usesIsoDirectly() {
        // Date-only events must land on the ISO date in every zone — no zone conversion.
        val note = noteWithDateSlot(start = "2025-01-15")
        assertEquals(LocalDate.of(2025, 1, 15).toEpochDay(), note.calendarLocalDayKey())
    }

    @Test
    fun appointmentView_timeSlot_mapsAllFields() {
        val note = noteWithTimeSlot(start = sampleEpochSeconds, end = sampleEpochSecondsPlus2h)
        val view = note.appointmentView()
        assertTrue(view != null)
        assertEquals("Demo", view!!.title)
        assertEquals(false, view.isAllDay)
        assertEquals(sampleEpochSeconds, view.startSeconds)
        assertEquals(sampleEpochSecondsPlus2h, view.endSeconds)
    }

    @Test
    fun appointmentView_dateSlot_isAllDayTrue() {
        val note = noteWithDateSlot(start = "2025-01-15", end = "2025-01-16")
        val view = note.appointmentView()
        assertTrue(view != null)
        assertEquals(true, view!!.isAllDay)
    }

    @Test
    fun appointmentView_nonCalendarNote_returnsNull() {
        val note = Note("no-event")
        assertNull(note.appointmentView())
    }

    @Test
    fun upcomingFirstOrder_upcomingBeforePast() {
        val now = sampleEpochSeconds
        val past = noteWithTimeSlot(id = "p", start = now - 3600)
        val future = noteWithTimeSlot(id = "f", start = now + 3600)
        val sorted = listOf(past, future).sortedWith(upcomingFirstCalendarOrder(now))
        assertEquals("f", sorted[0].idHex)
        assertEquals("p", sorted[1].idHex)
    }

    @Test
    fun upcomingFirstOrder_twoFutureEvents_nearestFirst() {
        val now = sampleEpochSeconds
        val nearFuture = noteWithTimeSlot(id = "near", start = now + 100)
        val farFuture = noteWithTimeSlot(id = "far", start = now + 10_000)
        val sorted = listOf(farFuture, nearFuture).sortedWith(upcomingFirstCalendarOrder(now))
        assertEquals("near", sorted[0].idHex)
        assertEquals("far", sorted[1].idHex)
    }

    @Test
    fun upcomingFirstOrder_twoPastEvents_mostRecentFirst() {
        val now = sampleEpochSeconds
        val recentPast = noteWithTimeSlot(id = "recent", start = now - 100)
        val ancientPast = noteWithTimeSlot(id = "ancient", start = now - 10_000)
        val sorted = listOf(ancientPast, recentPast).sortedWith(upcomingFirstCalendarOrder(now))
        assertEquals("recent", sorted[0].idHex)
        assertEquals("ancient", sorted[1].idHex)
    }

    @Test
    fun upcomingFirstOrder_isTransitive_acrossNowBoundary() {
        // Guards the previous bug where `TimeUtils.now()` was sampled inside the comparator: if
        // the clock moved while sorting, an event's "upcoming" classification could flip between
        // pair comparisons and trigger an IllegalArgumentException from the JDK sort. Snapshotting
        // `now` once eliminates that.
        val now = sampleEpochSeconds
        val notes =
            (0..20).map { i ->
                noteWithTimeSlot(id = "n$i", start = now + (i - 10) * 60L)
            }
        // Should not throw.
        notes.sortedWith(upcomingFirstCalendarOrder(now))
    }

    // ---- helpers ----

    private fun noteWithTimeSlot(
        id: String = "test-time",
        start: Long,
        end: Long? = null,
    ): Note {
        val tags =
            buildList {
                add(arrayOf("d", "$id-d"))
                add(arrayOf("title", "Demo"))
                add(arrayOf("start", start.toString()))
                end?.let { add(arrayOf("end", it.toString())) }
            }.toTypedArray()
        val event = CalendarTimeSlotEvent(id, "pub", 0L, tags, "content", "sig")
        return Note(id).apply { this.event = event }
    }

    private fun noteWithDateSlot(
        id: String = "test-date",
        start: String,
        end: String? = null,
    ): Note {
        val tags =
            buildList {
                add(arrayOf("d", "$id-d"))
                add(arrayOf("title", "Demo"))
                add(arrayOf("start", start))
                end?.let { add(arrayOf("end", it)) }
            }.toTypedArray()
        val event = CalendarDateSlotEvent(id, "pub", 0L, tags, "content", "sig")
        return Note(id).apply { this.event = event }
    }
}
