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
package com.vitorpamplona.amethyst.commons.model.nip52Calendar

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent

/**
 * Shared projection of NIP-52 calendar appointments. The 31922 (date-slot) and 31923 (time-slot)
 * event classes have identical UI surfaces but no common interface, so UI code repeated a
 * `when (event) { is Time -> e.title(); is Date -> e.title() }` block per accessor. Materialising
 * the projection once collapses those branches into a single linear read.
 *
 * [isAllDay] discriminates the two kinds; [startSeconds] is local-midnight for 31922 (matching
 * the rest of the calendar code's day-anchoring).
 */
@Immutable
data class CalendarAppointmentView(
    val title: String?,
    val image: String?,
    val summary: String?,
    val location: String?,
    val startSeconds: Long?,
    val endSeconds: Long?,
    val isAllDay: Boolean,
)

fun Note.appointmentView(): CalendarAppointmentView? =
    when (val e = event) {
        is CalendarTimeSlotEvent ->
            CalendarAppointmentView(
                title = e.title(),
                image = e.image(),
                summary = e.summary(),
                location = e.location(),
                startSeconds = e.start(),
                endSeconds = e.end() ?: e.start(),
                isAllDay = false,
            )
        is CalendarDateSlotEvent ->
            CalendarAppointmentView(
                title = e.title(),
                image = e.image(),
                summary = e.summary(),
                location = e.location(),
                startSeconds = parseIsoDateToUnixSeconds(e.start()),
                endSeconds = parseIsoDateToUnixSeconds(e.end()) ?: parseIsoDateToUnixSeconds(e.start()),
                isAllDay = true,
            )
        else -> null
    }
