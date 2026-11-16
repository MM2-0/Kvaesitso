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

/**
 * Formats instants with a Unicode LDML date skeleton ("yMMMd", "MMMd", "yMMM"), letting the
 * platform pick the default locale's field order ("MMM d, y" in en-US, "d MMM y" in en-GB).
 * Safe to call from any thread. Rebuilds itself when the default locale or time zone changes (iOS
 * keeps the zone it was built with).
 */
expect class DateSkeletonFormatter(
    skeleton: String,
) {
    fun format(epochMillis: Long): String
}

/** The instant's calendar year and day-of-year in the default time zone, as year * 1000 + day. */
expect fun calendarYearAndDay(epochMillis: Long): Int

/**
 * A time-of-day formatter ("14:32" / "2:32 PM"). Android and iOS follow the system 12/24-hour
 * setting; the JVM follows the locale. Identity-stable, so it can key a `remember`, and cheap to
 * call for every item in a feed: platforms whose formatter is costly share one cached instance.
 */
@Composable
expect fun rememberTimeOfDayFormatter(): (epochMillis: Long) -> String

/** Whether times should be shown on a 24-hour clock, under the same rules as [rememberTimeOfDayFormatter]. */
@Composable
expect fun rememberIs24HourClock(): Boolean

/**
 * The platform's abbreviated relative span ("5 min. ago", "Yesterday"), [nowLabel] for an
 * instant that rounds to now or lies ahead, or null where the platform has no such formatter.
 */
expect fun relativeTimeSpanShortOrNull(
    epochMillis: Long,
    nowMillis: Long,
    nowLabel: String,
): String?
