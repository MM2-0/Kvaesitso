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
package com.vitorpamplona.amethyst.commons.podcasts

private val WEEKDAYS = arrayOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")
private val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * [epochSeconds] as an RFC 2822 date in GMT (`Tue, 24 Jun 2025 12:00:00 GMT`), the form
 * Podcasting 2.0's `pubdate` and RSS use. Matches `java.time`'s `RFC_1123_DATE_TIME`, which
 * does not zero-pad the day.
 */
fun rfc2822Date(epochSeconds: Long): String {
    val days = epochSeconds.floorDiv(86_400L)
    val secondOfDay = epochSeconds.mod(86_400L)

    // Civil-from-days (Howard Hinnant), proleptic Gregorian.
    val z = days + 719_468L
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val day = doy - (153L * mp + 2L) / 5L + 1L
    val month = if (mp < 10L) mp + 3L else mp - 9L
    val year = yoe + era * 400L + if (month <= 2L) 1L else 0L

    val weekday = WEEKDAYS[days.mod(7L).toInt()]
    val hh = (secondOfDay / 3_600L).toString().padStart(2, '0')
    val mm = (secondOfDay / 60L % 60L).toString().padStart(2, '0')
    val ss = (secondOfDay % 60L).toString().padStart(2, '0')

    return "$weekday, $day ${MONTHS[(month - 1L).toInt()]} $year $hh:$mm:$ss GMT"
}
