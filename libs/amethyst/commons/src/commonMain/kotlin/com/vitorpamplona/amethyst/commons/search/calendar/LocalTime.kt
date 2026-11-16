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

import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The unix second at [hour]:[minute] local time on [date], following java.time's rules where a
 * clock change makes that wall time ambiguous or missing: the earlier offset wins in an overlap,
 * and a time skipped by a spring-forward gap moves forward by the gap's length.
 */
fun LocalClock.atTime(
    date: SearchDate,
    hour: Int,
    minute: Int,
): Long {
    val wall = date.daysFromEpoch() * TimeUtils.ONE_DAY + hour * TimeUtils.ONE_HOUR + minute * TimeUtils.ONE_MINUTE
    // The offsets in force a day either side bracket any change this wall time could fall in.
    val before = wall - utcOffsetSeconds(wall - TimeUtils.ONE_DAY)
    val after = wall - utcOffsetSeconds(wall + TimeUtils.ONE_DAY)
    return when {
        before + utcOffsetSeconds(before) == wall -> before
        after + utcOffsetSeconds(after) == wall -> after
        else -> before
    }
}

/** Seconds since local midnight at [unixSeconds], for seeding an hour and minute picker. */
fun LocalClock.secondOfDay(unixSeconds: Long): Int = (unixSeconds + utcOffsetSeconds(unixSeconds)).mod(TimeUtils.ONE_DAY)

/** The calendar day a Material date picker means by its UTC-midnight [utcMillis]. */
fun SearchDate.Companion.fromPickerMillis(utcMillis: Long): SearchDate = civilFromDays(utcMillis.floorDiv(TimeUtils.ONE_DAY * 1000L))
