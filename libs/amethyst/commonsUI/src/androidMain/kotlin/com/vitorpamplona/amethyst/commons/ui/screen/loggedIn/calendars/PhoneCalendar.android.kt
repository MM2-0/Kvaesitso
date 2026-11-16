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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.parseIsoDateToUnixSeconds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent

@Composable
actual fun rememberPhoneCalendar(): PhoneCalendar {
    val context = LocalContext.current
    return remember(context) { PhoneCalendar { addToPhoneCalendar(context, it) } }
}

/**
 * Opens the system "New Event" composer (Google Calendar / Samsung / iCloud / etc.) pre-populated
 * with this appointment's title, time, location, and description. The user sees their normal
 * calendar UI with one tap to "Save" — strictly nicer than the .ics-via-share-sheet path because
 * it doesn't go through a file and surfaces the user's preferred calendar app directly.
 *
 * Returns true if a calendar app handled the intent, false if no handler was found — the caller
 * can decide whether to fall back to the .ics share path.
 */
private fun addToPhoneCalendar(
    context: Context,
    event: Event,
): Boolean {
    val (title, location, summary) =
        when (event) {
            is CalendarTimeSlotEvent -> Triple(event.title(), event.location(), event.summary())
            is CalendarDateSlotEvent -> Triple(event.title(), event.location(), event.summary())
            else -> return false
        }
    val (beginMs, endMs, allDay) = computeRangeMs(event) ?: return false

    val description =
        buildString {
            summary?.let { append(it) }
            if (event.content.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(event.content)
            }
        }

    val intent =
        Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.orEmpty())
            location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            if (description.isNotEmpty()) {
                putExtra(CalendarContract.Events.DESCRIPTION, description)
            }
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * Computes the (beginMillis, endMillis, isAllDay) triple from a calendar event. NIP-52 date-slot
 * uses ISO dates that we anchor at local midnight; time-slot uses unix seconds. End defaults to
 * begin + 1 hour for time-slot events without an end and to begin + 1 day for all-day events
 * without an end (calendar providers expect end > start for any visible event).
 */
private fun computeRangeMs(event: Event): Triple<Long, Long, Boolean>? =
    when (event) {
        is CalendarTimeSlotEvent -> {
            val start = event.start() ?: return null
            val end = event.end() ?: (start + 3600L)
            Triple(start * 1000L, end * 1000L, false)
        }
        is CalendarDateSlotEvent -> {
            val startSec = parseIsoDateToUnixSeconds(event.start()) ?: return null
            val endSec = parseIsoDateToUnixSeconds(event.end()) ?: (startSec + 86400L)
            Triple(startSec * 1000L, endSec * 1000L, true)
        }
        else -> null
    }
