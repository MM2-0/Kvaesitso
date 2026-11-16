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
package com.vitorpamplona.amethyst.commons.relayClient.calendars

import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent

// Appointments are the only kinds shown in the main calendar feed/views. RSVPs and
// collections come along on the same subscription so detail screens can render without
// a second round-trip, but they don't drive the timeline DAL.
val CalendarAppointmentKinds = listOf(CalendarTimeSlotEvent.KIND, CalendarDateSlotEvent.KIND)

// What an author publishes as the owner of a calendar, as opposed to answering someone else's.
val CalendarOwnKinds = listOf(CalendarTimeSlotEvent.KIND, CalendarDateSlotEvent.KIND, CalendarCollectionEvent.KIND)

val CalendarRsvpKinds = listOf(CalendarRSVPEvent.KIND)

val AllCalendarKinds =
    listOf(
        CalendarTimeSlotEvent.KIND,
        CalendarDateSlotEvent.KIND,
        CalendarCollectionEvent.KIND,
        CalendarRSVPEvent.KIND,
    )
