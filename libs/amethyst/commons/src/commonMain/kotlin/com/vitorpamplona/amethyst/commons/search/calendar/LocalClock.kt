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

/**
 * The only parts of a calendar day that need to know where the reader is: which unix second a
 * local midnight falls on, which day it is here now, and how this locale spells a month and a
 * weekday. Everything else about a day is arithmetic and lives in [SearchDate] / [MonthGrid].
 *
 * `since:2026-04-01` means "written on or after midnight **here**", not midnight UTC — a search
 * saved in Auckland and reopened in São Paulo would otherwise silently shift by most of a day.
 */
expect object LocalClock {
    /** The unix second at 00:00:00 local time on [date]. */
    fun startOfDay(date: SearchDate): Long

    /**
     * The unix second at 23:59:59 local time on [date]. Not [startOfDay] plus 86,399: a local day
     * that crosses a clock change is 23 or 25 hours long, and NIP-01's `until` is inclusive.
     */
    fun endOfDay(date: SearchDate): Long

    /** Today, in the reader's timezone. */
    fun today(): SearchDate

    /** How many seconds this zone is ahead of UTC at [unixSeconds]; a clock change moves it. */
    fun utcOffsetSeconds(unixSeconds: Long): Long

    /**
     * How many seconds the IANA zone [zoneId] is ahead of UTC at [unixSeconds], or null when this
     * platform does not know the zone. Two ids name the same clock at an instant when these match
     * ("UTC" and "Etc/UTC"; Paris and Berlin).
     */
    fun utcOffsetSeconds(
        zoneId: String,
        unixSeconds: Long,
    ): Long?

    /**
     * Which local day a unix second falls on, with the zone read once for the whole counter
     * rather than per timestamp: for grouping a batch of events by day in one pass.
     */
    fun epochDayCounter(): EpochDayCounter

    /** Which weekday a week starts on here, 0 = Sunday. */
    fun firstDayOfWeek(): Int

    /** The reader's IANA time zone ("America/Sao_Paulo"), as NIP-52 events record it. */
    fun zoneId(): String

    /** The month above a calendar grid, in the reader's own spelling ("April 2026"). */
    fun monthLabel(date: SearchDate): String

    /** A day in the reader's own spelling ("1 Apr 2026"), for a pill and a grid cell's label. */
    fun dayLabel(date: SearchDate): String

    /** The seven column headings, narrow, indexed 0 = Sunday. The grid rotates them itself. */
    fun narrowWeekdayNames(): List<String>
}

/** Maps a unix second to the local day it falls on, counted in days since 1970-01-01. */
fun interface EpochDayCounter {
    fun epochDay(unixSeconds: Long): Long
}
