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
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vitorpamplona.amethyst.commons.search.calendar

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.utils.currentTimeSeconds
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName

// NSDateFormatter is not thread-safe for concurrent stringFromDate calls (Apple docs), and the
// search field re-reads a month grid from whichever dispatcher the state flow ran on. One lock
// over the shared formatters is cheaper than allocating one per grid cell.
private val labelLock = KmpLock()

private val monthFormatter: NSDateFormatter = formatter("LLLL yyyy")
private val dayFormatter: NSDateFormatter = formatter("d MMM yyyy")

private fun formatter(pattern: String): NSDateFormatter =
    NSDateFormatter().apply {
        dateFormat = pattern
        // localTimeZone is the auto-updating zone, so a device that crosses one keeps formatting
        // in the zone it is now in rather than the one the process started in.
        timeZone = NSTimeZone.localTimeZone
        locale = NSLocale.currentLocale
    }

private fun dateAt(epochSeconds: Long): NSDate = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())

/**
 * The one thing the day arithmetic needs from Foundation: how far ahead of UTC this zone is at an
 * instant, which a clock change moves. [ZoneMath] does the rest, in commonMain, where a test can
 * actually run it — this source set compiles off a Mac but never runs off one.
 */
private val localZone = ZoneOffsets { NSTimeZone.localTimeZone.secondsFromGMTForDate(dateAt(it)) }

actual object LocalClock {
    actual fun startOfDay(date: SearchDate): Long = ZoneMath.startOfDay(date, localZone)

    actual fun endOfDay(date: SearchDate): Long = ZoneMath.endOfDay(date, localZone)

    actual fun today(): SearchDate = ZoneMath.dayAt(currentTimeSeconds(), localZone)

    actual fun utcOffsetSeconds(unixSeconds: Long): Long = localZone.at(unixSeconds)

    actual fun utcOffsetSeconds(
        zoneId: String,
        unixSeconds: Long,
    ): Long? =
        NSTimeZone
            .timeZoneWithName(zoneId.trim())
            ?.secondsFromGMTForDate(dateAt(unixSeconds))

    actual fun epochDayCounter(): EpochDayCounter = EpochDayCounter { ZoneMath.epochDayAt(it, localZone) }

    /**
     * Which weekday a week starts on here, 0 = Sunday. `firstWeekday` follows the reader's own
     * region setting — Sunday across most of the Americas and East Asia, Monday across Europe,
     * Saturday across much of the Middle East — and is read per call so a settings change lands
     * without a restart. Foundation counts it 1..7 from Sunday; this API counts 0..6.
     */
    actual fun firstDayOfWeek(): Int {
        val sundayBased = NSCalendar.currentCalendar.firstWeekday.toInt() - 1
        // A calendar that answers outside 1..7 is not one this can lay out a week from; ISO
        // Monday shifts the grid's columns, where an out-of-range index would crash it.
        return if (sundayBased in 0..6) sundayBased else 1
    }

    actual fun zoneId(): String = NSTimeZone.localTimeZone.name

    actual fun monthLabel(date: SearchDate): String = labelLock.withLock { monthFormatter.stringFromDate(dateAt(startOfDay(date.firstOfMonth()))) }

    actual fun dayLabel(date: SearchDate): String = labelLock.withLock { dayFormatter.stringFromDate(dateAt(startOfDay(date))) }

    actual fun narrowWeekdayNames(): List<String> =
        labelLock.withLock {
            // veryShortWeekdaySymbols is already indexed 0 = Sunday, which is this API's order.
            val symbols = dayFormatter.veryShortWeekdaySymbols
            // A locale with no symbols is not one this can name days in; the ISO initials are
            // wrong-but-legible, where a short list would crash the grid's heading row.
            if (symbols.size == 7) symbols.map { it.toString() } else listOf("S", "M", "T", "W", "T", "F", "S")
        }
}
