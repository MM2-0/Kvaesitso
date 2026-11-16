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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.appointmentView
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.note.DateSkeletonFormatter
import com.vitorpamplona.amethyst.commons.ui.note.calendarYearAndDay

// Skeletons follow Unicode LDML — the platform picks the locale's field order.
private val dayMonthFormat = DateSkeletonFormatter("EEEMMMd") // "Mon, May 28" / "Mon 28 May"
private val fullDateFormat = DateSkeletonFormatter("EEEEMMMMdy") // "Monday, May 28, 2026" / "Monday, 28 May 2026"
private val monthYearFormat = DateSkeletonFormatter("MMMMy") // "May 2026" / "Mai 2026"
private val weekdayShortFormat = DateSkeletonFormatter("EEE") // "Mon" — order doesn't matter

/**
 * The appointment's date or time range. [formatTime] renders a time of day under the user's
 * 12/24-hour setting; composables get it from `rememberTimeOfDayFormatter()`.
 */
fun formatCalendarRange(
    note: Note,
    formatTime: (epochMillis: Long) -> String,
): String? {
    val view = note.appointmentView() ?: return null
    val start = view.startSeconds ?: return null
    return if (view.isAllDay) {
        formatDateRange(start, view.endSeconds)
    } else {
        formatTimeRange(start, view.endSeconds, formatTime)
    }
}

private fun formatTimeRange(
    start: Long,
    end: Long?,
    formatTime: (epochMillis: Long) -> String,
): String {
    val startMs = start * 1000
    val startStr = "${dayMonthFormat.format(startMs)} · ${formatTime(startMs)}"
    if (end == null || end == start) return startStr
    val endMs = end * 1000
    return if (isSameDay(startMs, endMs)) {
        "$startStr – ${formatTime(endMs)}"
    } else {
        "$startStr – ${dayMonthFormat.format(endMs)} · ${formatTime(endMs)}"
    }
}

private fun formatDateRange(
    start: Long,
    end: Long?,
): String {
    val startStr = dayMonthFormat.format(start * 1000)
    if (end == null || end == start) return startStr
    return "$startStr – ${dayMonthFormat.format(end * 1000)}"
}

fun formatLongDate(unixSeconds: Long): String = fullDateFormat.format(unixSeconds * 1000)

fun formatMonthYear(
    year: Int,
    monthZeroBased: Int,
): String = monthYearFormat.format(LocalClock.startOfDay(SearchDate(year, monthZeroBased + 1, 1)) * 1000)

fun formatTimeOfDay(
    unixSeconds: Long,
    formatTime: (epochMillis: Long) -> String,
): String = formatTime(unixSeconds * 1000)

/** The short name of a weekday, 0 = Sunday. */
fun formatShortWeekday(weekdayZeroBased: Int): String =
    // 2026-01-04 was a Sunday; any known Sunday works.
    weekdayShortFormat.format(LocalClock.startOfDay(SearchDate(2026, 1, 4 + weekdayZeroBased)) * 1000)

private fun isSameDay(
    aMs: Long,
    bMs: Long,
): Boolean = calendarYearAndDay(aMs) == calendarYearAndDay(bMs)
