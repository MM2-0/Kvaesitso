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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

// The field order DateTimeFormatter.ofLocalizedPattern accepts: era, year, quarter, month, week,
// weekday, day, day period, hour, minute, second, zone. It takes no other letter: `a` (AM/PM) is
// implied by `h`, so it and anything else outside the list are left out.
private val SKELETON_FIELD_ORDER = listOf("G", "y", "Q", "M", "w", "E", "d", "B", "hHjC", "m", "s", "vz")

/**
 * [skeleton]'s runs of letters in the order the JDK requires ("EEEMMMd" becomes "MMMEEEd").
 * The sort is stable, so letters of one field (h and j, v and z) keep their relative order.
 * Android's `getBestDateTimePattern` takes them in any order, and the shared code is written for
 * it; the result is the same pattern either way, since a skeleton names fields, not positions.
 */
internal fun canonicalSkeleton(skeleton: String): String {
    val runs = mutableListOf<String>()
    for (c in skeleton) {
        if (runs.isNotEmpty() && runs.last()[0] == c) runs[runs.size - 1] = runs.last() + c else runs.add(c.toString())
    }
    return runs
        .mapNotNull { run -> SKELETON_FIELD_ORDER.indexOfFirst { run[0] in it }.takeIf { it >= 0 }?.let { it to run } }
        .sortedBy { it.first }
        .joinToString("") { it.second }
}

/**
 * [DateTimeFormatter.ofLocalizedPattern] (JDK 19+) resolves the skeleton against the locale's
 * CLDR data, the JVM counterpart of Android's `getBestDateTimePattern`. The formatter is
 * immutable, so one cached instance serves every thread; it is rebuilt when the default locale
 * or time zone changes.
 */
actual class DateSkeletonFormatter actual constructor(
    private val skeleton: String,
) {
    private class Cached(
        val locale: Locale,
        val zone: ZoneId,
        val formatter: DateTimeFormatter,
    )

    @Volatile private var cached: Cached? = null

    actual fun format(epochMillis: Long): String {
        val locale = Locale.getDefault()
        val zone = ZoneId.systemDefault()
        val current = cached?.takeIf { it.locale == locale && it.zone == zone }
        val formatter =
            current?.formatter
                ?: DateTimeFormatter.ofLocalizedPattern(canonicalSkeleton(skeleton)).withLocale(locale).withZone(zone).also {
                    cached = Cached(locale, zone, it)
                }
        return formatter.format(Instant.ofEpochMilli(epochMillis))
    }
}

private class CachedTimeOfDay(
    val locale: Locale,
    val zone: ZoneId,
    val formatter: DateTimeFormatter,
)

@Volatile private var cachedTimeOfDay: CachedTimeOfDay? = null

/** One formatter for every item, rebuilt only when the locale or zone changes. */
private val timeOfDay: (Long) -> String = { epochMillis ->
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val formatter =
        cachedTimeOfDay?.takeIf { it.locale == locale && it.zone == zone }?.formatter
            ?: DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).also {
                cachedTimeOfDay = CachedTimeOfDay(locale, zone, it)
            }
    formatter.format(Instant.ofEpochMilli(epochMillis))
}

@Composable
actual fun rememberTimeOfDayFormatter(): (epochMillis: Long) -> String = timeOfDay

@Composable
actual fun rememberIs24HourClock(): Boolean =
    remember {
        DateTimeFormatterBuilder
            .getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, Locale.getDefault())
            .contains('H')
    }

actual fun relativeTimeSpanShortOrNull(
    epochMillis: Long,
    nowMillis: Long,
    nowLabel: String,
): String? = null
