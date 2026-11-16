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

import com.vitorpamplona.amethyst.commons.model.RelayGroupTargetCandidate
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.redirectStrayRelayGroupContent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The serving-relay hazard router: a stray group-scoped content event (no channel keyed to its serving
 * relay) must be redirected to the group's single confirmed **host** channel, never to another phantom, and
 * never when the host is ambiguous. Also pins that a phantom channel — one that only ever received content —
 * reports no relay-signed state, which is what keeps [redirectStrayRelayGroupContent] from ever picking one.
 */
class RelayGroupContentRoutingTest {
    private val relayA = RelayUrlNormalizer.normalizeOrNull("wss://relay-a.example/")!!
    private val relayB = RelayUrlNormalizer.normalizeOrNull("wss://relay-b.example/")!!
    private val relayC = RelayUrlNormalizer.normalizeOrNull("wss://relay-c.example/")!!

    private fun host(
        id: String,
        relay: NormalizedRelayUrl,
    ) = RelayGroupTargetCandidate(GroupId(id, relay), hasRelaySignedState = true)

    private fun phantom(
        id: String,
        relay: NormalizedRelayUrl,
    ) = RelayGroupTargetCandidate(GroupId(id, relay), hasRelaySignedState = false)

    @Test
    fun `no candidates leaves the routing to the caller`() {
        // A genuinely new group on the serving relay: caller keeps the serving-relay key.
        assertNull(redirectStrayRelayGroupContent(emptyList()))
    }

    @Test
    fun `a single confirmed host is chosen`() {
        assertEquals(GroupId("g1", relayA), redirectStrayRelayGroupContent(listOf(host("g1", relayA))))
    }

    @Test
    fun `a phantom-only candidate is never chosen`() {
        // Redirecting a stray onto another phantom would just move it into a second unread channel.
        assertNull(redirectStrayRelayGroupContent(listOf(phantom("g1", relayB))))
    }

    @Test
    fun `the confirmed host wins over a phantom for the same id`() {
        assertEquals(
            GroupId("g1", relayA),
            redirectStrayRelayGroupContent(listOf(phantom("g1", relayB), host("g1", relayA))),
        )
    }

    @Test
    fun `an id claimed by two confirmed hosts is ambiguous and left to the caller`() {
        assertNull(redirectStrayRelayGroupContent(listOf(host("g1", relayA), host("g1", relayC))))
    }

    @Test
    fun `several phantoms and no host returns null`() {
        assertNull(redirectStrayRelayGroupContent(listOf(phantom("g1", relayA), phantom("g1", relayB))))
    }

    @Test
    fun `a fresh channel has no relay-signed state until a relay-signed event flips it`() {
        val channel = RelayGroupChannel(GroupId("g1", relayA))
        assertFalse("a channel with no relay-signed events is a phantom, not a host", channel.hasRelaySignedState())

        channel.updatePinned(
            GroupPinnedEvent(
                id = "d".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1L,
                tags = arrayOf(arrayOf("d", "g1"), arrayOf("e", "a".repeat(64))),
                content = "",
                sig = "0".repeat(128),
            ),
        )
        assertTrue("a relay-signed pin list makes it a confirmed host", channel.hasRelaySignedState())
    }
}
