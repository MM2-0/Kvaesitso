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

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** [LocalClock.atTime] and [LocalClock.secondOfDay] against java.time, the code they replace. */
class LocalTimeTest {
    private val zones = listOf("America/New_York", "Asia/Kathmandu", "America/Santiago", "Pacific/Chatham", "Europe/London", "UTC")

    // Ordinary days plus both 2026 clock changes in New York, London and Santiago.
    private val dates =
        listOf(
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 3, 8),
            LocalDate.of(2026, 3, 29),
            LocalDate.of(2026, 4, 5),
            LocalDate.of(2026, 9, 6),
            LocalDate.of(2026, 10, 25),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(1969, 12, 31),
        )

    private inline fun inEveryZone(check: (ZoneId) -> Unit) {
        val saved = TimeZone.getDefault()
        try {
            for (id in zones) {
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                check(ZoneId.of(id))
            }
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun atTimeMatchesJavaTimeIncludingGapsAndOverlaps() =
        inEveryZone { zone ->
            for (day in dates) {
                val date = SearchDate(day.year, day.monthValue, day.dayOfMonth)
                for (hour in 0..23) {
                    for (minute in listOf(0, 30, 59)) {
                        val expected = LocalDateTime.of(day.year, day.monthValue, day.dayOfMonth, hour, minute).atZone(zone).toEpochSecond()
                        assertEquals(expected, LocalClock.atTime(date, hour, minute), "$zone $day $hour:$minute")
                    }
                }
            }
        }

    @Test
    fun secondOfDayMatchesJavaTime() =
        inEveryZone { zone ->
            for (t in listOf(-1L, 0L, 1772945999L, 1772946000L, 1793509200L) + (0 until 200).map { 1767225600L + it * 43_211L }) {
                val expected =
                    Instant
                        .ofEpochSecond(t)
                        .atZone(zone)
                        .toLocalTime()
                        .toSecondOfDay()
                assertEquals(expected, LocalClock.secondOfDay(t), "$zone at $t")
            }
        }

    @Test
    fun pickerMillisAreTheUtcCalendarDay() {
        assertEquals(SearchDate(2026, 3, 8), SearchDate.fromPickerMillis(1772928000000L))
        assertEquals(SearchDate(1969, 12, 31), SearchDate.fromPickerMillis(-86_400_000L))
    }
}
