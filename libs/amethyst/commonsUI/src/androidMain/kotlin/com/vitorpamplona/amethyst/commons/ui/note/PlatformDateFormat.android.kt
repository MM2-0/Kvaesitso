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

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.text.DateFormat as JavaDateFormat

/**
 * Per-thread cached [SimpleDateFormat] keyed off the current default [Locale] and time zone.
 *
 * `SimpleDateFormat` is mutable and not thread-safe, and these formatters are read from both
 * the UI thread (composition) and background coroutines. `ThreadLocal` gives each thread its
 * own instance: no locks, and a lazy rebuild on a locale or time-zone change. A formatter fixes
 * its zone when built, so without the zone in the key a trip across zones would keep printing
 * dates in the old one.
 */
actual class DateSkeletonFormatter actual constructor(
    private val skeleton: String,
) {
    private class Cached(
        val locale: Locale,
        val zoneId: String,
        val formatter: SimpleDateFormat,
    )

    private val cache = ThreadLocal<Cached>()

    private fun get(): SimpleDateFormat {
        val locale = Locale.getDefault()
        val zoneId = TimeZone.getDefault().id
        val cached = cache.get()
        if (cached != null && cached.locale == locale && cached.zoneId == zoneId) return cached.formatter
        val fresh = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        cache.set(Cached(locale, zoneId, fresh))
        return fresh
    }

    actual fun format(epochMillis: Long): String = get().format(epochMillis)
}

private class CachedTimeOfDay(
    val is24Hour: Boolean,
    val locale: Locale,
    val zoneId: String,
    val formatter: JavaDateFormat,
)

private val timeOfDayCache = ThreadLocal<CachedTimeOfDay>()

/**
 * The system's time-of-day format. The 12/24-hour setting, the locale and the zone can all change
 * under a running app, so they are re-read on every call; the formatter is rebuilt only when one
 * of them did. Building one per call was a pattern parse on every timestamp a feed drew.
 */
private fun timeOfDayFormat(context: Context): JavaDateFormat {
    val is24Hour = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()
    val zoneId = TimeZone.getDefault().id
    val cached = timeOfDayCache.get()
    if (cached != null && cached.is24Hour == is24Hour && cached.locale == locale && cached.zoneId == zoneId) return cached.formatter
    val fresh = DateFormat.getTimeFormat(context)
    timeOfDayCache.set(CachedTimeOfDay(is24Hour, locale, zoneId, fresh))
    return fresh
}

@Composable
actual fun rememberTimeOfDayFormatter(): (epochMillis: Long) -> String {
    val context = LocalContext.current
    return remember(context) { { epochMillis -> timeOfDayFormat(context).format(Date(epochMillis)) } }
}

@Composable
actual fun rememberIs24HourClock(): Boolean {
    val context = LocalContext.current
    return remember(context) { DateFormat.is24HourFormat(context) }
}

actual fun relativeTimeSpanShortOrNull(
    epochMillis: Long,
    nowMillis: Long,
    nowLabel: String,
): String? {
    val humanReadable =
        DateUtils
            .getRelativeTimeSpanString(
                epochMillis,
                nowMillis,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL,
            ).toString()
    return if (humanReadable.startsWith("In") || humanReadable.startsWith("0")) nowLabel else humanReadable
}
