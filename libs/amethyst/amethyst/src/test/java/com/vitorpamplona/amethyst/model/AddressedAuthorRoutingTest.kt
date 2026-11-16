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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.EventBroadcaster
import com.vitorpamplona.amethyst.commons.model.addressedAuthors
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [addressedAuthors] is what lets [EventBroadcaster] reach an addressed author's inbox relays
 * without a cache hit. The rest of the a-tag branch is nested inside
 * `getAddressableNoteIfExists(addressId)`, so before this existed an RSVP answering an
 * appointment this device had never cached went only to the sender's own outbox — the host it
 * was replying to never received it.
 */
class AddressedAuthorRoutingTest {
    private val host = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val relay = "wss://relay.damus.io/"

    private fun rsvp(vararg tags: Array<String>) =
        CalendarRSVPEvent(
            id = "00".repeat(32),
            pubKey = "11".repeat(32),
            createdAt = 1700000000,
            tags = arrayOf(*tags),
            content = "",
            sig = "00".repeat(64),
        )

    private fun calendar(vararg tags: Array<String>) =
        CalendarCollectionEvent(
            id = "22".repeat(32),
            pubKey = host,
            createdAt = 1700000000,
            tags = arrayOf(*tags),
            content = "",
            sig = "00".repeat(64),
        )

    @Test
    fun `an RSVP addresses the appointment's host`() {
        val event = rsvp(arrayOf("a", "31923:$host:party", relay), arrayOf("status", "accepted"))

        assertEquals(setOf(host), addressedAuthors(event))
    }

    @Test
    fun `the host is found without a relay hint on the tag`() {
        // The hint is optional; the coordinate alone must still identify who to deliver to.
        val event = rsvp(arrayOf("a", "31923:$host:party"), arrayOf("status", "declined"))

        assertEquals(setOf(host), addressedAuthors(event))
    }

    @Test
    fun `a day-slot appointment resolves the same way`() {
        val event = rsvp(arrayOf("a", "31922:$host:all-day"), arrayOf("status", "tentative"))

        assertEquals(setOf(host), addressedAuthors(event))
    }

    @Test
    fun `a calendar addresses every appointment author it lists`() {
        val event =
            calendar(
                arrayOf("d", "my-calendar"),
                arrayOf("a", "31923:$host:party", relay),
                arrayOf("a", "31922:$other:standup"),
            )

        assertEquals(setOf(host, other), addressedAuthors(event))
    }

    @Test
    fun `repeated authors collapse to one delivery target`() {
        // A calendar usually lists many appointments by the same host; their inbox is one target.
        val event =
            calendar(
                arrayOf("d", "my-calendar"),
                arrayOf("a", "31923:$host:party"),
                arrayOf("a", "31923:$host:standup"),
                arrayOf("a", "31922:$host:all-day"),
            )

        assertEquals(setOf(host), addressedAuthors(event))
    }

    @Test
    fun `a malformed coordinate is dropped rather than throwing`() {
        val event =
            rsvp(
                arrayOf("a", "not-a-coordinate"),
                arrayOf("a", "31923:$host:party"),
                arrayOf("status", "accepted"),
            )

        assertEquals(setOf(host), addressedAuthors(event))
    }

    @Test
    fun `an event with no a tags addresses nobody`() {
        assertTrue(addressedAuthors(rsvp(arrayOf("status", "accepted"))).isEmpty())
    }
}
