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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone

/**
 * The reader's own clock, for two questions the front page asks: which day an edition belongs
 * to ("Today", "Yesterday"), and when the next morning edition is due. Read per call, never
 * cached, so a device that crosses a time zone answers in the zone it is in now.
 */
expect object FrontPageClock {
    /** The local calendar day of [epochSec], as `yyyy-MM-dd`. */
    fun dayKey(epochSec: Long): String

    /** The next instant, strictly after [nowEpochSec], when the local clock reads [hour]:[minute]. */
    fun nextRun(
        nowEpochSec: Long,
        hour: Int,
        minute: Int,
    ): Long

    /** The instant today's [hour]:[minute] fell, or falls, on the local clock. */
    fun todayAt(
        nowEpochSec: Long,
        hour: Int,
        minute: Int,
    ): Long

    /**
     * This device's IANA time zone, e.g. `America/New_York`; null where the platform can't name
     * it here (iOS, for now): then nothing is written in a zone from this device.
     */
    fun localZone(): String?

    /**
     * The zone named [zone] (IANA) for cron; null when it is missing or this platform doesn't know
     * it. NIP-FB: a `cron` without a known zone is unreadable, never read in the device's zone.
     */
    fun cronZone(zone: String?): CronZone?
}

/** ISO day of week of a `yyyy-MM-dd` day, 1 = Monday … 7 = Sunday; null for anything else. */
fun dayOfWeek(day: String): Int? {
    val parts = day.split('-')
    if (parts.size != 3) return null
    var y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val d = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    // Sakamoto's method: 0 = Sunday.
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    if (m < 3) y -= 1
    val sundayZero = (y + y / 4 - y / 100 + y / 400 + t[m - 1] + d) % 7
    return if (sundayZero == 0) 7 else sundayZero
}
