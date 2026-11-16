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

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy")
private val dayFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun SearchDate.toJava(): LocalDate = LocalDate.of(year, month, day)

actual object LocalClock {
    // Read per call, never cached: a device crossing a timezone must not keep answering with
    // the zone the process started in.
    private fun zone(): ZoneId = ZoneId.systemDefault()

    actual fun startOfDay(date: SearchDate): Long = date.toJava().atStartOfDay(zone()).toEpochSecond()

    // The second before the next midnight, so a day that gained or lost an hour still ends where
    // it ends.
    actual fun endOfDay(date: SearchDate): Long =
        date
            .toJava()
            .plusDays(1)
            .atStartOfDay(zone())
            .toEpochSecond() - 1

    actual fun today(): SearchDate =
        LocalDate.now(zone()).let {
            SearchDate(it.year, it.monthValue, it.dayOfMonth)
        }

    actual fun utcOffsetSeconds(unixSeconds: Long): Long =
        zone()
            .rules
            .getOffset(Instant.ofEpochSecond(unixSeconds))
            .totalSeconds
            .toLong()

    actual fun utcOffsetSeconds(
        zoneId: String,
        unixSeconds: Long,
    ): Long? {
        val zone =
            try {
                ZoneId.of(zoneId.trim())
            } catch (e: DateTimeException) {
                return null
            }
        return zone.rules
            .getOffset(Instant.ofEpochSecond(unixSeconds))
            .totalSeconds
            .toLong()
    }

    actual fun epochDayCounter(): EpochDayCounter {
        val rules = zone().rules
        val offsets = ZoneOffsets { rules.getOffset(Instant.ofEpochSecond(it)).totalSeconds.toLong() }
        return EpochDayCounter { ZoneMath.epochDayAt(it, offsets) }
    }

    // WeekFields counts DayOfWeek 1..7 from Monday; this API counts 0..6 from Sunday.
    actual fun firstDayOfWeek(): Int = WeekFields.of(Locale.getDefault()).firstDayOfWeek.value % 7

    actual fun zoneId(): String = ZoneId.systemDefault().id

    actual fun monthLabel(date: SearchDate): String = date.firstOfMonth().toJava().format(monthFormatter)

    actual fun dayLabel(date: SearchDate): String = date.toJava().format(dayFormatter)

    actual fun narrowWeekdayNames(): List<String> {
        // 2026-01-04 is a Sunday, so seven days from it spell the week Sunday-first.
        val sunday = LocalDate.of(2026, 1, 4)
        return (0..6).map {
            sunday.plusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
        }
    }
}
