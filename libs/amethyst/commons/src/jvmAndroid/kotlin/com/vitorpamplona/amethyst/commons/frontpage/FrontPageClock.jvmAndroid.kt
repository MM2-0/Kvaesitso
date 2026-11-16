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
import com.vitorpamplona.quartz.nipFBScheduledTasks.ZoneIdCronZone
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

actual object FrontPageClock {
    private fun zone(): ZoneId = ZoneId.systemDefault()

    actual fun dayKey(epochSec: Long): String = dayKeyIn(zone(), epochSec)

    actual fun nextRun(
        nowEpochSec: Long,
        hour: Int,
        minute: Int,
    ): Long = nextRunIn(zone(), nowEpochSec, hour, minute)

    actual fun todayAt(
        nowEpochSec: Long,
        hour: Int,
        minute: Int,
    ): Long = atIn(zone(), nowEpochSec, hour, minute, plusDays = 0)

    actual fun localZone(): String? = zone().id.takeIf { ZoneIdCronZone.of(it) != null }

    actual fun cronZone(zone: String?): CronZone? = zone?.let { ZoneIdCronZone.of(it) }

    internal fun dayKeyIn(
        zone: ZoneId,
        epochSec: Long,
    ): String =
        Instant
            .ofEpochSecond(epochSec)
            .atZone(zone)
            .toLocalDate()
            .toString()

    /**
     * ZonedDateTime resolves the two awkward days for us: a time that does not exist (the hour
     * the clocks jump forward over) moves forward by the gap, and a time that happens twice (the
     * hour they fall back over) takes the earlier one, so a morning edition is never skipped and
     * never printed twice.
     */
    internal fun nextRunIn(
        zone: ZoneId,
        nowEpochSec: Long,
        hour: Int,
        minute: Int,
    ): Long {
        val today = atIn(zone, nowEpochSec, hour, minute, plusDays = 0)
        return if (today > nowEpochSec) today else atIn(zone, nowEpochSec, hour, minute, plusDays = 1)
    }

    private fun atIn(
        zone: ZoneId,
        nowEpochSec: Long,
        hour: Int,
        minute: Int,
        plusDays: Long,
    ): Long {
        val day =
            Instant
                .ofEpochSecond(nowEpochSec)
                .atZone(zone)
                .toLocalDate()
                .plusDays(plusDays)
        return ZonedDateTime.of(day, LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)), zone).toEpochSecond()
    }
}
