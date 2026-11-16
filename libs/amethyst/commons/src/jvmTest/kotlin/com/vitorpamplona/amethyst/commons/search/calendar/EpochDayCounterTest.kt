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
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** [LocalClock.epochDayCounter] against the java.time chain notification cards used to call. */
class EpochDayCounterTest {
    private val zones = listOf("America/New_York", "Asia/Kathmandu", "America/Santiago", "Pacific/Chatham", "UTC")

    // Around New York's 2026 changes, Santiago's midnight change, the epoch and before it.
    private val instants =
        listOf(
            -86401L,
            -1L,
            0L,
            1L,
            1772945999L,
            1772946000L,
            1773028799L,
            1773028800L,
            1788667199L,
            1788667200L,
            1793509199L,
            1793509200L,
            1793512800L,
        ) + (0 until 400).map { 1767225600L + it * 21_613L }

    @Test
    fun matchesJavaTimeInEveryZone() {
        val saved = TimeZone.getDefault()
        try {
            for (id in zones) {
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                val zone = ZoneId.of(id)
                val counter = LocalClock.epochDayCounter()
                for (t in instants) {
                    val expected =
                        Instant
                            .ofEpochSecond(t)
                            .atZone(zone)
                            .toLocalDate()
                            .toEpochDay()
                    assertEquals(expected, counter.epochDay(t), "$id at $t")
                }
            }
        } finally {
            TimeZone.setDefault(saved)
        }
    }
}
