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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.CalendarAppointmentView
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_relative_ongoing
import com.vitorpamplona.amethyst.commons.resources.calendar_relative_ongoing_with_end
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.relativeTimeSpan
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Localised "starts in 2 hours" / "started 5 minutes ago" / "Happening now · ends in 2 hours"
 * label for an appointment. Returns null when the event has no parseable start (nothing to
 * anchor a relative phrase to).
 *
 * Uses the platform's relative-time formatter ([relativeTimeSpan]) for the underlying minute/hour/day phrasing — that
 * helper is locale-aware and ages from "just now" through "in N days" to absolute date for
 * far-out events. For all-day events we extend the resolution to DAY so we get "tomorrow",
 * "in 3 days" instead of an hour-precision phrase that would lie about the start moment.
 *
 * Ongoing events (start ≤ now ≤ end) get a composite "Happening now · ends in X" so a user
 * mid-event sees how much time is left rather than the misleading "started X minutes ago" that
 * the formatter would produce on its own.
 */
suspend fun relativeTimeLabel(
    view: CalendarAppointmentView,
    nowSeconds: Long,
): String? {
    val start = view.startSeconds ?: return null
    val end = view.endSeconds

    if (end != null && start <= nowSeconds && nowSeconds <= end) {
        val ongoing = loadStringRes(Res.string.calendar_relative_ongoing)
        val endsIn = relativeTimeSpan(end, nowSeconds, view.isAllDay)
        return loadStringRes(Res.string.calendar_relative_ongoing_with_end, ongoing, endsIn)
    }

    return relativeTimeSpan(start, nowSeconds, view.isAllDay)
}

/**
 * Composition-side wrapper for [relativeTimeLabel].
 *
 * The label is assembled from the string catalog, whose only non-composable
 * accessor suspends, so it is produced off composition and lands as state.
 */
@Composable
fun rememberRelativeTimeLabel(
    view: CalendarAppointmentView,
    key: Any?,
): String? {
    val label by produceState<String?>(initialValue = null, key, view.startSeconds) {
        value = relativeTimeLabel(view, TimeUtils.now())
    }
    return label
}
