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
package com.vitorpamplona.amethyst.calendar

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in the tag shape [com.vitorpamplona.amethyst.commons.ui.note.types.CalendarRsvpRow] assembles.
 *
 * The `p` count is the load-bearing assertion. Kind 31925 is in `NOTIFICATION_KINDS`, and
 * `NotificationFeedFilter.tagsAnEventByUser` returns true for it (a `BaseAddressableEvent` falls
 * through every `BaseNoteEvent` branch), so every pubkey tagged here gets a notification row for
 * this RSVP. Tagging the appointment's other invitees would therefore hand each of them one row
 * per co-invitee per answer change, and buys no routing: `EventBroadcaster` already reaches them
 * by following the a-tag into the appointment, whose own p tags it reads as pubkey hints.
 */
class RsvpTagAssemblyTest {
    private val host = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val apptId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val relay = "wss://relay.damus.io/"

    private fun buildRsvp(): Array<Array<String>> {
        val target = Address(31923, host, "party")
        val hint = RelayUrlNormalizer.normalizeOrNull(relay)

        return CalendarRSVPEvent
            .build(
                calendarEventAddress = ATag(target, hint),
                status = RSVPStatusTag.STATUS.ACCEPTED,
                calendarEventId = ETag(apptId, hint, host),
                calendarEventAuthor = PTag(host),
                dTag = "rsvp-d",
            ).tags
    }

    @Test
    fun tagsTheAppointmentCoordinateAndThePinnedRevision() {
        val tags = buildRsvp()

        // The `a` tag follows the host's later edits; the `e` tag pins the revision answered.
        assertEquals("31923:$host:party", tags.single { it[0] == "a" }[1])
        assertEquals(apptId, tags.single { it[0] == "e" }[1])
        assertEquals("rsvp-d", tags.single { it[0] == "d" }[1])
        assertEquals("accepted", tags.single { it[0] == "status" }[1])
    }

    @Test
    fun tagsExactlyOnePubkeyAndItIsTheHost() {
        val pTags = buildRsvp().filter { it[0] == "p" }

        assertEquals(listOf(host), pTags.map { it[1] })
    }

    @Test
    fun theHostRemainsTheFirstPubkeySoCalendarEventAuthorResolves() {
        val tags = buildRsvp()
        val event =
            CalendarRSVPEvent(
                id = "00".repeat(32),
                pubKey = "11".repeat(32),
                createdAt = 1700000000,
                tags = tags,
                content = "",
                sig = "00".repeat(64),
            )

        assertEquals(host, event.calendarEventAuthor()?.pubKey)
        assertEquals(apptId, event.calendarEventId()?.eventId)
    }
}
