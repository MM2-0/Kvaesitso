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
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent

/**
 * One appointment and the people (already narrowed to the ones the viewer cares about) whose
 * latest RSVP to it says "going". [attendees] is newest-RSVP first, so the faces a row shows are
 * the people who decided most recently.
 *
 * A data class on purpose: the fold re-runs on every RSVP or appointment that lands, and value
 * equality is what lets the StateFlow drop a fold that changed nothing and lets an unchanged row
 * skip recomposition. [appointment] compares by identity, which is right — the cache hands out
 * one [AddressableNote] per address.
 */
@Immutable
data class AppointmentAttendance(
    val appointment: AddressableNote,
    val attendees: List<HexKey>,
)

/**
 * [upcoming] is what a "where are my follows going" list draws, soonest first. [unresolved] are
 * the appointments someone RSVP'd to that the cache has no event for yet, most recently answered
 * first: whether they are upcoming at all is unknown until they load, so they are handed back for
 * the caller to fetch instead of being drawn as blank rows. The order matters to a caller that
 * can only fetch a bounded number — a fresh RSVP is the one most likely to be for a future event.
 */
@Immutable
data class FollowsGoing(
    val upcoming: List<AppointmentAttendance>,
    val unresolved: List<AddressableNote>,
)

/**
 * Folds a pile of kind-31925 RSVPs into the appointments the accepted authors are going to.
 *
 * - Only an author's **latest** RSVP to a given appointment counts. RSVPs are addressable with a
 *   free-form `d` tag, so a person who said "going" and later "can't go" may have two live events
 *   (two d-tags); the newest one is their answer.
 * - [isAttendee] runs on that latest RSVP only: the follow-list / mute check belongs to the caller.
 * - [isAppointmentVisible] runs on the appointment's address before it is resolved, so an event
 *   hosted by someone the viewer muted is neither drawn nor fetched.
 * - An appointment counts as upcoming while it has not ended (an ongoing multi-day conference is
 *   still somewhere your friends are), matching the feed lens's upcoming/past split.
 *
 * [nowSeconds] is a parameter so the split is deterministic under test.
 */
fun computeFollowsGoing(
    rsvps: Collection<CalendarRSVPEvent>,
    isAttendee: (CalendarRSVPEvent) -> Boolean,
    appointmentFor: (Address) -> AddressableNote,
    nowSeconds: Long,
    isAppointmentVisible: (Address) -> Boolean = { true },
): FollowsGoing {
    val latest = latestRsvpAnswers(rsvps)

    val goingByAppointment = HashMap<Address, MutableList<CalendarRSVPEvent>>()
    latest.forEach { (key, rsvp) ->
        if (rsvp.status() == RSVPStatusTag.STATUS.ACCEPTED && isAttendee(rsvp)) {
            goingByAppointment.getOrPut(key.first) { mutableListOf() }.add(rsvp)
        }
    }

    val upcoming = ArrayList<Pair<Long, AppointmentAttendance>>()
    val unresolved = ArrayList<Pair<Long, AddressableNote>>()

    goingByAppointment.forEach { (address, going) ->
        if (!isAppointmentVisible(address)) return@forEach

        going.sortWith(NEWEST_FIRST)

        val appointment = appointmentFor(address)
        if (appointment.event == null) {
            unresolved.add(going.first().createdAt to appointment)
            return@forEach
        }
        val start = appointment.calendarStartSeconds() ?: return@forEach
        val end = appointment.calendarEndSeconds() ?: start
        if (end < nowSeconds) return@forEach

        upcoming.add(start to AppointmentAttendance(appointment, going.map { it.pubKey }))
    }

    upcoming.sortWith(
        compareBy<Pair<Long, AppointmentAttendance>> { it.first }
            .thenByDescending { it.second.attendees.size }
            .thenBy { it.second.appointment.idHex },
    )
    unresolved.sortWith(
        compareByDescending<Pair<Long, AddressableNote>> { it.first }
            .thenBy { it.second.idHex },
    )

    return FollowsGoing(upcoming.map { it.second }, unresolved.map { it.second })
}

/**
 * Each author's newest RSVP to each appointment, keyed (appointment, author). RSVPs are
 * addressable with a free-form `d` tag, so one person can hold several live answers to the same
 * appointment (said "going", then "can't go" under a new d tag); only the newest is their answer.
 * RSVPs pointing at anything but an appointment kind are skipped.
 */
fun latestRsvpAnswers(rsvps: Collection<CalendarRSVPEvent>): Map<Pair<Address, HexKey>, CalendarRSVPEvent> {
    val latest = HashMap<Pair<Address, HexKey>, CalendarRSVPEvent>()
    rsvps.forEach { rsvp ->
        val target = rsvp.calendarEventAddress() ?: return@forEach
        if (target.kind != CalendarTimeSlotEvent.KIND && target.kind != CalendarDateSlotEvent.KIND) return@forEach
        val key = target to rsvp.pubKey
        val current = latest[key]
        if (current == null || rsvp.isNewerThan(current)) {
            latest[key] = rsvp
        }
    }
    return latest
}

/** Whether this RSVP is its author's current answer in [latest] (see [latestRsvpAnswers]). */
fun CalendarRSVPEvent.isLatestAnswerIn(latest: Map<Pair<Address, HexKey>, CalendarRSVPEvent>): Boolean {
    val target = calendarEventAddress() ?: return false
    return latest[target to pubKey]?.id == id
}

// Newest first; the id breaks a same-second tie so the answer never depends on input order.
private val NEWEST_FIRST =
    compareByDescending<CalendarRSVPEvent> { it.createdAt }.thenBy { it.id }

private fun CalendarRSVPEvent.isNewerThan(other: CalendarRSVPEvent) = NEWEST_FIRST.compare(this, other) < 0
