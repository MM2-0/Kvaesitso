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
package com.vitorpamplona.amethyst.commons.model.nip52Calendar

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsExportTest {
    private val nowSeconds = 1_700_000_000L // 2023-11-14 22:13:20 UTC

    @Test
    fun timeSlot_producesWellFormedVCalendar() {
        // 1700000000 = 2023-11-14 22:13:20 UTC; +1h = 2023-11-14 23:13:20 UTC.
        val event =
            CalendarTimeSlotEvent(
                id = "id",
                pubKey = "pub",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("d", "my-event"),
                        arrayOf("title", "Bitcoin meetup"),
                        arrayOf("start", "1700000000"),
                        arrayOf("end", "1700003600"),
                        arrayOf("location", "Storgata 8"),
                    ),
                content = "Casual hang",
                sig = "sig",
            )
        val ics = IcsExport.appointmentToIcs(event, Address(31923, "pub", "my-event"), nowSeconds)

        // Outer envelope is required by every RFC 5545 parser.
        assertTrue("must wrap in VCALENDAR", ics.contains("BEGIN:VCALENDAR\r\n"))
        assertTrue("must close VCALENDAR", ics.endsWith("END:VCALENDAR\r\n"))
        assertTrue("must include VEVENT", ics.contains("BEGIN:VEVENT\r\n"))
        assertTrue("must close VEVENT", ics.contains("END:VEVENT\r\n"))
        assertTrue("must have version", ics.contains("VERSION:2.0"))

        // Time-slot stamps are UTC instants.
        assertTrue("DTSTART must be UTC instant", ics.contains("DTSTART:20231114T221320Z"))
        assertTrue("DTEND must be UTC instant", ics.contains("DTEND:20231114T231320Z"))

        assertTrue("summary present", ics.contains("SUMMARY:Bitcoin meetup"))
        assertTrue("location present", ics.contains("LOCATION:Storgata 8"))
        assertTrue("description present", ics.contains("DESCRIPTION:Casual hang"))
        assertTrue("UID format", ics.contains("UID:my-event@pub.nostr"))
    }

    @Test
    fun dateSlot_emitsDateValueWithoutTimeComponent() {
        val event =
            CalendarDateSlotEvent(
                id = "id",
                pubKey = "pub",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("d", "conf"),
                        arrayOf("title", "Conference"),
                        arrayOf("start", "2025-01-15"),
                        arrayOf("end", "2025-01-17"),
                    ),
                content = "",
                sig = "sig",
            )
        val ics = IcsExport.appointmentToIcs(event, Address(31922, "pub", "conf"), nowSeconds)

        // Date-only events use VALUE=DATE so calendar apps don't render them as midnight events.
        assertTrue("date-slot DTSTART carries VALUE=DATE", ics.contains("DTSTART;VALUE=DATE:20250115"))
        assertTrue("date-slot DTEND carries VALUE=DATE", ics.contains("DTEND;VALUE=DATE:20250117"))
        assertFalse("date-slot must not include time component", ics.contains("DTSTART:20250115T"))
    }

    @Test
    fun escapeText_quotesSpecialCharacters() {
        // RFC 5545 §3.3.11: backslash, comma, semicolon, newline are reserved.
        assertEquals("a\\\\b", IcsExport.escapeText("a\\b"))
        assertEquals("a\\,b", IcsExport.escapeText("a,b"))
        assertEquals("a\\;b", IcsExport.escapeText("a;b"))
        assertEquals("line1\\nline2", IcsExport.escapeText("line1\nline2"))
        assertEquals("a", IcsExport.escapeText("a\r"))
    }

    @Test
    fun escapingFiresInSummaryAndDescription() {
        val event =
            CalendarTimeSlotEvent(
                id = "id",
                pubKey = "pub",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("d", "x"),
                        arrayOf("title", "Hi, world; happy"),
                        arrayOf("start", "1700000000"),
                    ),
                content = "line1\nline2",
                sig = "sig",
            )
        val ics = IcsExport.appointmentToIcs(event, Address(31923, "pub", "x"), nowSeconds)
        // The escaped string must keep the surrounding `SUMMARY:` prefix and survive whatever
        // line folding parsers re-apply.
        assertTrue("comma escaped", ics.contains("SUMMARY:Hi\\, world\\; happy"))
        assertTrue("newline escaped", ics.contains("DESCRIPTION:line1\\nline2"))
    }

    @Test
    fun hashtags_renderAsCategoriesLine() {
        val event =
            CalendarTimeSlotEvent(
                id = "id",
                pubKey = "pub",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("d", "x"),
                        arrayOf("title", "T"),
                        arrayOf("start", "1700000000"),
                        arrayOf("t", "bitcoin"),
                        arrayOf("t", "meetup"),
                    ),
                content = "",
                sig = "sig",
            )
        val ics = IcsExport.appointmentToIcs(event, Address(31923, "pub", "x"), nowSeconds)
        assertTrue("CATEGORIES line present", ics.contains("CATEGORIES:bitcoin,meetup"))
    }

    @Test
    fun calendarToIcs_wrapsAllMembers() {
        val a =
            CalendarTimeSlotEvent(
                id = "a",
                pubKey = "pub",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("d", "a"),
                        arrayOf("title", "A"),
                        arrayOf("start", "1700000000"),
                    ),
                content = "",
                sig = "sig",
            )
        val b =
            CalendarDateSlotEvent(
                id = "b",
                pubKey = "pub",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("d", "b"),
                        arrayOf("title", "B"),
                        arrayOf("start", "2025-02-01"),
                    ),
                content = "",
                sig = "sig",
            )
        val calendar =
            CalendarCollectionEvent(
                id = "cal",
                pubKey = "pub",
                createdAt = 0L,
                tags = arrayOf(arrayOf("d", "my-cal"), arrayOf("title", "My Calendar")),
                content = "All my events",
                sig = "sig",
            )
        val ics =
            IcsExport.calendarToIcs(
                calendar,
                listOf(
                    Address(31923, "pub", "a") to a,
                    Address(31922, "pub", "b") to b,
                ),
                nowSeconds,
            )

        assertTrue("calendar name present", ics.contains("X-WR-CALNAME:My Calendar"))
        assertTrue("calendar description present", ics.contains("X-WR-CALDESC:All my events"))
        // Both members must appear inside the one VCALENDAR.
        assertEquals(
            "exactly two VEVENT blocks",
            2,
            "BEGIN:VEVENT".toRegex().findAll(ics).count(),
        )
        assertTrue("member A present", ics.contains("UID:a@pub.nostr"))
        assertTrue("member B present", ics.contains("UID:b@pub.nostr"))
    }

    @Test
    fun filename_sanitisesPathSeparatorsAndSpaces() {
        val event =
            CalendarTimeSlotEvent(
                id = "id",
                pubKey = "pub",
                createdAt = 0L,
                tags =
                    arrayOf(
                        arrayOf("d", "x"),
                        arrayOf("title", "Slashes / & spaces .,;"),
                        arrayOf("start", "1700000000"),
                    ),
                content = "",
                sig = "sig",
            )
        val name = IcsExport.appointmentFilename(event, Address(31923, "pub", "x"))
        // Must end with .ics and contain no filesystem-hostile characters.
        assertTrue(name.endsWith(".ics"))
        assertFalse("no slashes", name.contains('/'))
        assertFalse("no commas", name.contains(','))
        assertFalse("no semicolons", name.contains(';'))
    }

    @Test
    fun filename_fallsBackToDTagWhenTitleAbsent() {
        val event =
            CalendarTimeSlotEvent(
                id = "id",
                pubKey = "pub",
                createdAt = 0L,
                tags = arrayOf(arrayOf("d", "fallback-dtag"), arrayOf("start", "1700000000")),
                content = "",
                sig = "sig",
            )
        val name = IcsExport.appointmentFilename(event, Address(31923, "pub", "fallback-dtag"))
        assertEquals("fallback-dtag.ics", name)
    }
}
