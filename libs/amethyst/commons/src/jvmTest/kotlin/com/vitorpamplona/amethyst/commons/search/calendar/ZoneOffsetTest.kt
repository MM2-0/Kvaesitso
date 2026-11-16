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

import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** [LocalClock.utcOffsetSeconds] for a named zone, which the calendar card compares to the reader's. */
class ZoneOffsetTest {
    // 2026-04-08 18:00 UTC: both Paris and Berlin are on summer time (UTC+2).
    private val summer = 1775671200L

    // 2026-01-15 12:00 UTC: winter time (UTC+1).
    private val winter = 1768478400L

    @Test
    fun aliasesOfTheSameOffsetMatch() {
        assertEquals(LocalClock.utcOffsetSeconds("UTC", summer), LocalClock.utcOffsetSeconds("Etc/UTC", summer))
        assertEquals(LocalClock.utcOffsetSeconds("Europe/Paris", summer), LocalClock.utcOffsetSeconds("Europe/Berlin", summer))
        assertEquals(7200L, LocalClock.utcOffsetSeconds("Europe/Paris", summer))
        assertEquals(3600L, LocalClock.utcOffsetSeconds("Europe/Paris", winter))
    }

    @Test
    fun differentOffsetsDiffer() {
        assertNotEquals(LocalClock.utcOffsetSeconds("Europe/London", summer), LocalClock.utcOffsetSeconds("Europe/Paris", summer))
    }

    @Test
    fun unknownZoneHasNoOffset() {
        assertNull(LocalClock.utcOffsetSeconds("Not/A_Zone", summer))
        assertNull(LocalClock.utcOffsetSeconds("", summer))
    }

    @Test
    fun readerOffsetMatchesTheirNamedZone() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            assertEquals(LocalClock.utcOffsetSeconds(summer), LocalClock.utcOffsetSeconds("Europe/Paris", summer))
        } finally {
            TimeZone.setDefault(saved)
        }
    }
}
