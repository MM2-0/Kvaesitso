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

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrontPageClockTest {
    private val newYork = ZoneId.of("America/New_York")

    private fun at(
        zone: ZoneId,
        text: String,
    ) = ZonedDateTime.parse(text + zone.rules.getOffset(LocalDateTime.parse(text)).id + "[" + zone.id + "]").toEpochSecond()

    private fun local(
        zone: ZoneId,
        sec: Long,
    ) = Instant
        .ofEpochSecond(sec)
        .atZone(zone)
        .toLocalDateTime()
        .toString()

    @Test
    fun laterTodayOrElseTomorrow() {
        val sixAm = at(newYork, "2026-11-10T06:00")
        assertEquals("2026-11-10T07:30", local(newYork, FrontPageClock.nextRunIn(newYork, sixAm, 7, 30)))
        val eightAm = at(newYork, "2026-11-10T08:00")
        assertEquals("2026-11-11T07:30", local(newYork, FrontPageClock.nextRunIn(newYork, eightAm, 7, 30)))
        // Strictly after: the run that is due right now is not due again.
        val due = at(newYork, "2026-11-10T07:30")
        assertTrue(FrontPageClock.nextRunIn(newYork, due, 7, 30) > due)
    }

    @Test
    fun aTimeTheClocksSkipMovesForwardInsteadOfVanishing() {
        // 2026-03-08: New York jumps from 02:00 to 03:00, so 02:30 never happens that day.
        val night = at(newYork, "2026-03-08T00:00")
        assertEquals("2026-03-08T03:30", local(newYork, FrontPageClock.nextRunIn(newYork, night, 2, 30)))
    }

    @Test
    fun aTimeTheClocksRepeatPrintsOnceAtTheFirst() {
        // 2026-11-01: 01:30 happens twice in New York; the first one, never both.
        val night = at(newYork, "2026-11-01T00:00")
        val first = FrontPageClock.nextRunIn(newYork, night, 1, 30)
        assertEquals("2026-11-01T01:30", local(newYork, first))
        val next = FrontPageClock.nextRunIn(newYork, first, 1, 30)
        assertEquals("2026-11-02T01:30", local(newYork, next))
        // 25 hours later, not 1: the second 01:30 of that night is skipped.
        assertEquals(25 * 3600L, next - first)
    }

    @Test
    fun theDayIsTheReadersDay() {
        // 03:00 UTC on the 11th is still the evening of the 10th in New York.
        val utc = ZonedDateTime.parse("2026-11-11T03:00Z").toEpochSecond()
        assertEquals("2026-11-10", FrontPageClock.dayKeyIn(newYork, utc))
        assertEquals("2026-11-11", FrontPageClock.dayKeyIn(ZoneId.of("UTC"), utc))
    }
}
