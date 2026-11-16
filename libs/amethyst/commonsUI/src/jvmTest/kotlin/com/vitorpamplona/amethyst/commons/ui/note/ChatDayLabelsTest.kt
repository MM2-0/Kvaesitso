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
package com.vitorpamplona.amethyst.commons.ui.note

import org.junit.After
import org.junit.Before
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** The wording of chat stamps and day labels, pinned to UTC and US English. */
class ChatDayLabelsTest {
    private lateinit var savedLocale: Locale
    private lateinit var savedZone: TimeZone

    @Before
    fun pin() {
        savedLocale = Locale.getDefault()
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun restore() {
        Locale.setDefault(savedLocale)
        TimeZone.setDefault(savedZone)
    }

    // Thu 2026-11-05 15:00 UTC
    private val now = 1793890800L * 1000
    private val timeOfDay: (Long) -> String = { "5:20 PM" }

    private fun stamp(seconds: Long) = chatTimestampWith(seconds, now, timeOfDay, "Yesterday")

    private fun day(seconds: Long) = chatDayLabelWith(seconds, now, "Today", "Yesterday")

    @Test
    fun todayIsTheTimeOfDay() = assertEquals("5:20 PM", stamp(1793869200L))

    @Test
    fun yesterdaySaysSo() = assertEquals("Yesterday, 5:20 PM", stamp(1793833200L))

    @Test
    fun thisWeekNamesTheWeekday() = assertEquals("Mon, 5:20 PM", stamp(1793640000L))

    @Test
    fun aWeekAgoIsADate() = assertEquals("Oct 29", stamp(1793294400L))

    @Test
    fun earlierThisYearIsMonthAndDay() = assertEquals("Aug 20", stamp(1787246400L))

    @Test
    fun anotherYearCarriesTheYear() = assertEquals("Jan 5, 2024", stamp(1704475200L))

    @Test
    fun lastNightIsYesterdayNotToday() {
        // 11 PM yesterday is only 16 hours ago; the old divider called anything under 24 hours "Today".
        assertEquals("Yesterday", day(1793833200L))
        assertEquals("Today", day(1793869200L))
        assertEquals("Mon, 5:20 PM", stamp(1793640000L))
        assertEquals("Nov 2", day(1793640000L))
        assertEquals("Jan 5, 2024", day(1704475200L))
    }
}
