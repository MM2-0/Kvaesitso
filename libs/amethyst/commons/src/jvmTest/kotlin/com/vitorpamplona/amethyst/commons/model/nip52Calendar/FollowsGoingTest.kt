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

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowsGoingTest {
    private val now = 1_800_000_000L
    private val host = "a".repeat(64)
    private val alice = "1".repeat(64)
    private val bob = "2".repeat(64)
    private val carol = "3".repeat(64)

    private val notes = HashMap<Address, AddressableNote>()

    private fun appointmentFor(address: Address) = notes.getOrPut(address) { AddressableNote(address) }

    private fun timeSlot(
        dTag: String,
        start: Long,
        end: Long? = null,
    ): Address {
        val address = Address(CalendarTimeSlotEvent.KIND, host, dTag)
        val tags =
            buildList {
                add(arrayOf("d", dTag))
                add(arrayOf("title", dTag))
                add(arrayOf("start", start.toString()))
                end?.let { add(arrayOf("end", it.toString())) }
            }.toTypedArray()
        appointmentFor(address).event = CalendarTimeSlotEvent("id-$dTag", host, 0L, tags, "", "sig")
        return address
    }

    private var rsvpCounter = 0

    private fun rsvp(
        author: String,
        target: Address,
        status: String,
        createdAt: Long,
        dTag: String = "rsvp-${rsvpCounter++}",
    ) = CalendarRSVPEvent(
        id = "rsvp-${rsvpCounter++}",
        pubKey = author,
        createdAt = createdAt,
        tags =
            arrayOf(
                arrayOf("d", dTag),
                arrayOf("a", target.toValue()),
                arrayOf("status", status),
            ),
        content = "",
        sig = "sig",
    )

    private fun fold(
        rsvps: List<CalendarRSVPEvent>,
        isAttendee: (CalendarRSVPEvent) -> Boolean = { true },
    ) = computeFollowsGoing(rsvps, isAttendee, ::appointmentFor, now)

    @Test
    fun groupsGoingAuthorsPerAppointment_newestAnswerFirst() {
        val conf = timeSlot("conf", start = now + 86_400)

        val result =
            fold(
                listOf(
                    rsvp(alice, conf, "accepted", createdAt = 10),
                    rsvp(bob, conf, "accepted", createdAt = 20),
                ),
            )

        assertEquals(1, result.upcoming.size)
        assertEquals(listOf(bob, alice), result.upcoming.single().attendees)
    }

    @Test
    fun onlyTheLatestAnswerPerAuthorCounts_evenAcrossDTags() {
        val conf = timeSlot("conf", start = now + 86_400)

        // Alice said going, then changed her mind in a second RSVP with a different d tag.
        val result =
            fold(
                listOf(
                    rsvp(alice, conf, "accepted", createdAt = 10, dTag = "first"),
                    rsvp(alice, conf, "declined", createdAt = 20, dTag = "second"),
                    rsvp(bob, conf, "tentative", createdAt = 30),
                ),
            )

        assertTrue(result.upcoming.isEmpty())
    }

    @Test
    fun attendeeFilterRunsOnTheLatestAnswer() {
        val conf = timeSlot("conf", start = now + 86_400)

        val result =
            fold(
                listOf(
                    rsvp(alice, conf, "accepted", createdAt = 10),
                    rsvp(carol, conf, "accepted", createdAt = 20),
                ),
                isAttendee = { it.pubKey != carol },
            )

        assertEquals(listOf(alice), result.upcoming.single().attendees)
    }

    @Test
    fun dropsEndedEvents_keepsOngoingOnes_sortsSoonestFirst() {
        val past = timeSlot("past", start = now - 7_200, end = now - 3_600)
        val ongoing = timeSlot("ongoing", start = now - 3_600, end = now + 3_600)
        val later = timeSlot("later", start = now + 10 * 86_400)
        val sooner = timeSlot("sooner", start = now + 86_400)

        val result =
            fold(
                listOf(past, ongoing, later, sooner).map { rsvp(alice, it, "accepted", createdAt = 10) },
            )

        assertEquals(
            listOf(ongoing, sooner, later),
            result.upcoming.map { it.appointment.address },
        )
    }

    @Test
    fun appointmentsNotInTheCacheAreReportedForFetching() {
        val missing = Address(CalendarDateSlotEvent.KIND, host, "not-loaded")

        val result = fold(listOf(rsvp(alice, missing, "accepted", createdAt = 10)))

        assertTrue(result.upcoming.isEmpty())
        assertEquals(listOf(missing), result.unresolved.map { it.address })
    }

    @Test
    fun ignoresRsvpsThatPointAtNonAppointments() {
        val calendar = Address(CalendarCollectionEvent.KIND, host, "cal")

        val result = fold(listOf(rsvp(alice, calendar, "accepted", createdAt = 10)))

        assertTrue(result.upcoming.isEmpty())
        assertTrue(result.unresolved.isEmpty())
    }

    @Test
    fun appointmentsByAHiddenHostAreNeitherShownNorFetched() {
        val conf = timeSlot("conf", start = now + 86_400)
        val missing = Address(CalendarTimeSlotEvent.KIND, host, "not-loaded")

        val result =
            computeFollowsGoing(
                rsvps = listOf(rsvp(alice, conf, "accepted", 10), rsvp(alice, missing, "accepted", 11)),
                isAttendee = { true },
                appointmentFor = ::appointmentFor,
                nowSeconds = now,
                isAppointmentVisible = { it.pubKeyHex != host },
            )

        assertTrue(result.upcoming.isEmpty())
        assertTrue(result.unresolved.isEmpty())
    }

    @Test
    fun unresolvedAreOrderedByTheMostRecentRsvp() {
        val old = Address(CalendarTimeSlotEvent.KIND, host, "old")
        val fresh = Address(CalendarTimeSlotEvent.KIND, host, "fresh")
        val middle = Address(CalendarTimeSlotEvent.KIND, host, "middle")

        val result =
            fold(
                listOf(
                    rsvp(alice, old, "accepted", createdAt = 10),
                    rsvp(alice, fresh, "accepted", createdAt = 30),
                    rsvp(alice, middle, "accepted", createdAt = 15),
                    // Bob's newer answer makes "middle" the most recent of all.
                    rsvp(bob, middle, "accepted", createdAt = 40),
                ),
            )

        assertEquals(listOf(middle, fresh, old), result.unresolved.map { it.address })
    }

    @Test
    fun anUnchangedFoldIsEqualToThePreviousOne() {
        val conf = timeSlot("conf", start = now + 86_400)
        val rsvps = listOf(rsvp(alice, conf, "accepted", 10), rsvp(bob, conf, "accepted", 20))

        // The view model's StateFlow relies on this to drop no-op folds.
        assertEquals(fold(rsvps), fold(rsvps.reversed()))
    }

    @Test
    fun anOlderGoingIsNotTheCurrentAnswerOnceTheAuthorDeclines() {
        // The Home feed's rule: the "going" under the old d tag is still live, but it is not
        // Alice's answer any more.
        val conf = timeSlot("conf", start = now + 86_400)
        val going = rsvp(alice, conf, "accepted", createdAt = 10)
        val declined = rsvp(alice, conf, "declined", createdAt = 20)

        val latest = latestRsvpAnswers(listOf(going, declined))

        assertTrue(declined.isLatestAnswerIn(latest))
        assertFalse(going.isLatestAnswerIn(latest))
    }

    @Test
    fun anAnswerStaysCurrentWhenOnlyOtherPeopleOrOtherEventsChange() {
        val conf = timeSlot("conf", start = now + 86_400)
        val party = timeSlot("party", start = now + 2 * 86_400)
        val aliceConf = rsvp(alice, conf, "accepted", createdAt = 10)
        val aliceParty = rsvp(alice, party, "declined", createdAt = 30)
        val bobConf = rsvp(bob, conf, "declined", createdAt = 40)

        val latest = latestRsvpAnswers(listOf(aliceConf, aliceParty, bobConf))

        assertTrue(aliceConf.isLatestAnswerIn(latest))
    }
}
