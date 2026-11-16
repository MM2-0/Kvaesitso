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
package com.vitorpamplona.amethyst.ui.note.types

import com.vitorpamplona.amethyst.commons.ui.note.types.startsWithinAWeek
import com.vitorpamplona.quartz.utils.TimeUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The RSVP strip's "in 3 days" label only shows while it says something the date does not: past
 * a week the formatter falls back to the same date the badge already shows.
 */
class CalendarRsvpStripLabelTest {
    private val now = 1_800_000_000L

    @Test
    fun showsForAnEventWithinAWeek() {
        assertTrue(startsWithinAWeek(now + 3 * TimeUtils.ONE_DAY, now))
        assertTrue(startsWithinAWeek(now + TimeUtils.ONE_WEEK, now))
    }

    @Test
    fun showsForAnEventThatAlreadyStarted() {
        assertTrue(startsWithinAWeek(now - TimeUtils.ONE_HOUR, now))
    }

    @Test
    fun hidesPastAWeekAndWithoutAStart() {
        assertFalse(startsWithinAWeek(now + TimeUtils.ONE_WEEK + 1, now))
        assertFalse(startsWithinAWeek(now + 33 * TimeUtils.ONE_DAY, now))
        assertFalse(startsWithinAWeek(null, now))
    }
}
