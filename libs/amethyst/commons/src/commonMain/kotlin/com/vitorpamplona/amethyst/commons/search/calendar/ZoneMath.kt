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
 * How far ahead of UTC a zone is at an instant. A zone that observes a clock change answers
 * differently on either side of it, which is the whole reason [ZoneMath] has to iterate.
 */
internal fun interface ZoneOffsets {
    /** Seconds this zone is ahead of UTC at [epochSeconds]. Negative west of Greenwich. */
    fun at(epochSeconds: Long): Long
}

/**
 * Turning a civil day into unix seconds, given nothing but a zone's offset at an instant.
 *
 * A platform that hands out a full calendar object (java.time's `atStartOfDay`) needs none of
 * this; Foundation has one too, behind `NSCalendar`. The reason the iOS actual does not reach
 * for it is that Apple source sets only *compile* off a Mac — their tests cannot run there — so
 * anything expressed in Foundation calls is unexercised until someone opens Xcode. Borrowing a
 * single offset lookup and doing the day arithmetic here puts every clock change under
 * commonTest instead.
 */
internal object ZoneMath {
    const val SECONDS_PER_DAY = 86400L

    /**
     * The unix second at local 00:00 on [date].
     *
     * The instant `t` we want satisfies `t + offset(t) = utcMidnight`, and the offset depends on
     * the instant it is asked about, so this is a fixed point rather than a subtraction. Two
     * probes reach it: the first guesses with the offset in force at UTC midnight, the second
     * re-asks at the instant that guess produced. When both probes report the same offset the
     * guess is the answer, and that covers every ordinary day plus the two interesting ones —
     * a day whose clocks move at 2am (the change is after midnight, so both probes sit on the
     * same side of it) and a day whose midnight hour is *repeated*, where the earlier of the two
     * midnights is the one that converges, matching `java.time`.
     *
     * They disagree only when local midnight does not exist at all: a zone that springs forward
     * *at* midnight (Chile, Cuba, Iran have all done this) skips straight from 23:59:59 to 01:00,
     * and no instant maps to 00:00. There the later probe is the first instant of the day that
     * does exist, which is what a `since:` bound wants.
     */
    fun startOfDay(
        date: SearchDate,
        offsets: ZoneOffsets,
    ): Long {
        val utcMidnight = date.daysFromEpoch() * SECONDS_PER_DAY
        val firstPass = utcMidnight - offsets.at(utcMidnight)
        val secondPass = utcMidnight - offsets.at(firstPass)
        val settled = offsets.at(secondPass)
        if (settled == utcMidnight - secondPass) return secondPass
        // Midnight was skipped. Both probes bracket the gap; the later one is on the far side.
        return maxOf(secondPass, utcMidnight - settled)
    }

    /**
     * The unix second at local 23:59:59 on [date] — the second before the next local midnight,
     * so a day that gained or lost an hour still ends where it ends. Never midnight plus 86,399.
     */
    fun endOfDay(
        date: SearchDate,
        offsets: ZoneOffsets,
    ): Long = startOfDay(date.plusDays(1), offsets) - 1

    /** The civil day [epochSeconds] falls on, in the zone [offsets] describes. */
    fun dayAt(
        epochSeconds: Long,
        offsets: ZoneOffsets,
    ): SearchDate = SearchDate.civilFromDays(epochDayAt(epochSeconds, offsets))

    /** The local day [epochSeconds] falls on, counted in days since 1970-01-01. */
    fun epochDayAt(
        epochSeconds: Long,
        offsets: ZoneOffsets,
    ): Long = (epochSeconds + offsets.at(epochSeconds)).floorDiv(SECONDS_PER_DAY)
}
