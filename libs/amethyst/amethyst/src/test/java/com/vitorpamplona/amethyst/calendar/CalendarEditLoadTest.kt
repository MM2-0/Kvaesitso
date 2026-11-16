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

import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smoke tests for the parsing path used by [NewCalendarEventViewModel.loadForEdit]. The VM
 * itself can't be instantiated in a JVM test without the Account graph, but the per-field
 * extraction logic delegates to Quartz accessors that are pure functions on the parsed event —
 * exercising those here proves the round-trip from on-the-wire tags back to populated form
 * fields.
 */
class CalendarEditLoadTest {
    @Test
    fun timeSlot_roundTripsAllFields() {
        val event =
            CalendarTimeSlotEvent(
                id = "id",
                pubKey = "pub",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("d", "d-tag"),
                        arrayOf("title", "Bitcoin meetup"),
                        arrayOf("start", "1775671200"),
                        arrayOf("end", "1775674800"),
                        arrayOf("start_tzid", "Europe/Oslo"),
                        arrayOf("summary", "An evening of stacking"),
                        arrayOf("image", "https://example.com/img.png"),
                        arrayOf("location", "Storgata 8"),
                        arrayOf("t", "bitcoin"),
                        arrayOf("t", "meetup"),
                    ),
                content = "Body",
                sig = "sig",
            )

        assertEquals("Bitcoin meetup", event.title())
        assertEquals(1775671200L, event.start())
        assertEquals(1775674800L, event.end())
        assertEquals("Europe/Oslo", event.startTzId())
        assertEquals("An evening of stacking", event.summary())
        assertEquals("https://example.com/img.png", event.image())
        assertEquals("Storgata 8", event.location())
        assertEquals(listOf("bitcoin", "meetup"), event.hashtags())
    }

    @Test
    fun dateSlot_roundTripsAllFields() {
        val event =
            CalendarDateSlotEvent(
                id = "id",
                pubKey = "pub",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("d", "d-tag"),
                        arrayOf("title", "Conference"),
                        arrayOf("start", "2025-01-15"),
                        arrayOf("end", "2025-01-17"),
                        arrayOf("summary", "Three day affair"),
                        arrayOf("image", "https://example.com/banner.png"),
                        arrayOf("location", "Lisbon"),
                        arrayOf("t", "tech"),
                    ),
                content = "Body",
                sig = "sig",
            )

        assertEquals("Conference", event.title())
        assertEquals("2025-01-15", event.start())
        assertEquals("2025-01-17", event.end())
        assertEquals("Three day affair", event.summary())
        assertEquals("https://example.com/banner.png", event.image())
        assertEquals("Lisbon", event.location())
        assertEquals(listOf("tech"), event.hashtags())
    }

    @Test
    fun emptyFields_returnSafeDefaults() {
        // An event with no optional tags should parse without exceptions; the VM substitutes
        // empty-string defaults in those cases.
        val event =
            CalendarTimeSlotEvent(
                id = "id",
                pubKey = "pub",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("d", "d-tag"),
                        arrayOf("title", "Bare"),
                        arrayOf("start", "1000000"),
                    ),
                content = "",
                sig = "sig",
            )

        assertEquals("Bare", event.title())
        assertEquals(1000000L, event.start())
        assertEquals(null, event.end())
        assertEquals(null, event.summary())
        assertEquals(null, event.image())
        assertEquals(null, event.location())
        assertEquals(emptyList<String>(), event.hashtags())
    }
}
