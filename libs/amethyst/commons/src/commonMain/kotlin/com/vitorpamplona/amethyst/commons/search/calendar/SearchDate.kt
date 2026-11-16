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

import androidx.compose.runtime.Immutable

/**
 * A calendar day, with no clock and no timezone attached: the `YYYY-MM-DD` a `since:`/`until:`
 * token is written as. Turning one into a unix second is [LocalClock]'s job, because that is the
 * only part of a day that needs to know where the reader is.
 *
 * The arithmetic here is Howard Hinnant's days-from-civil, which is exact for every proleptic
 * Gregorian date and needs no platform calendar — so February's lengths and the weekday a month
 * starts on are testable in commonTest.
 */
@Immutable
data class SearchDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<SearchDate> {
    /** The `YYYY-MM-DD` this day is written as inside a `since:`/`until:` token. */
    fun ymd(): String = "${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}"

    override fun compareTo(other: SearchDate): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return day.compareTo(other.day)
    }

    /** 0 = Sunday … 6 = Saturday. */
    fun dayOfWeek(): Int = ((daysFromEpoch() + 4).mod(7))

    fun daysFromEpoch(): Long = daysFromCivil(year, month, day)

    fun plusDays(days: Int): SearchDate = civilFromDays(daysFromEpoch() + days)

    /** The same day-of-month `months` away, clamped to the shorter month's last day (Jan 31 → Feb 28). */
    fun plusMonths(months: Int): SearchDate {
        val total = year * 12L + (month - 1) + months
        val y = total.floorDiv(12L).toInt()
        val m = total.mod(12L).toInt() + 1
        return SearchDate(y, m, day.coerceAtMost(lastDayOfMonth(y, m)))
    }

    /** The 1st of the month this day is in, `months` away — the grid's own unit of position. */
    fun firstOfMonth(months: Int = 0): SearchDate {
        val total = year * 12L + (month - 1) + months
        val y = total.floorDiv(12L).toInt()
        val m = total.mod(12L).toInt() + 1
        return SearchDate(y, m, 1)
    }

    fun sameMonth(other: SearchDate?): Boolean = other != null && year == other.year && month == other.month

    companion object {
        private fun pad(
            value: Int,
            width: Int,
        ) = value.toString().padStart(width, '0')

        fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

        fun lastDayOfMonth(
            year: Int,
            month: Int,
        ): Int =
            when (month) {
                1, 3, 5, 7, 8, 10, 12 -> 31
                4, 6, 9, 11 -> 30
                2 -> if (isLeapYear(year)) 29 else 28
                else -> 0
            }

        /**
         * The day this text names, or null when it names none: only the ISO spelling is read, and
         * `2026-02-31` is refused rather than rolled over into March.
         */
        fun parse(text: String?): SearchDate? {
            val s = text ?: return null
            if (s.length != 10 || s[4] != '-' || s[7] != '-') return null
            // Digits only: toIntOrNull alone would let a sign through ("2026-+1-05").
            for (i in s.indices) if (i != 4 && i != 7 && s[i] !in '0'..'9') return null
            val year = s.substring(0, 4).toIntOrNull() ?: return null
            val month = s.substring(5, 7).toIntOrNull() ?: return null
            val day = s.substring(8, 10).toIntOrNull() ?: return null
            return of(year, month, day)
        }

        /** The day these fields name, or null when they name none. */
        fun of(
            year: Int,
            month: Int,
            day: Int,
        ): SearchDate? {
            if (month < 1 || month > 12) return null
            if (day < 1 || day > lastDayOfMonth(year, month)) return null
            return SearchDate(year, month, day)
        }

        /** Days since 1970-01-01, exact for every proleptic Gregorian date. */
        fun daysFromCivil(
            year: Int,
            month: Int,
            day: Int,
        ): Long {
            val y = (if (month <= 2) year - 1 else year).toLong()
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146097 + doe - 719468
        }

        /** The inverse of [daysFromCivil]. */
        fun civilFromDays(days: Long): SearchDate {
            val z = days + 719468
            val era = (if (z >= 0) z else z - 146096) / 146097
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val y = yoe + era * 400
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = if (mp < 10) mp + 3 else mp - 9
            return SearchDate((if (m <= 2) y + 1 else y).toInt(), m.toInt(), d.toInt())
        }
    }
}
