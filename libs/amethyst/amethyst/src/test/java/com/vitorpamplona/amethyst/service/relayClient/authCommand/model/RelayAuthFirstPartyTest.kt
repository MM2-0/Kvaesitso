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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthFirstParty
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-account NIP-42 signing gate: on the shared client, an account signs a relay's AUTH only
 * when the relay is in its own relay list, or it is publishing its own event there. The bug these
 * lock down: an unpaid/bystander account being AUTH'd (and billed by inbox.nostr.wine) purely
 * because another account uses that relay — including via a merged filter that names it.
 */
class RelayAuthFirstPartyTest {
    private val relay = NormalizedRelayUrl("wss://inbox.nostr.wine/")
    private val me = "a".repeat(64)
    private val other = "b".repeat(64)

    private fun event(pubkey: String) =
        Event(
            id = "0".repeat(64),
            pubKey = pubkey,
            createdAt = 0,
            kind = 1059,
            tags = emptyArray(),
            content = "",
            sig = "",
        )

    @Test
    fun aRelayNotInMyListWithNothingOfMineIsNotFirstParty() {
        // The exact inbox.nostr.wine case: a relay another account uses, which a merged read filter
        // names me on, but which is in none of my lists and where I publish nothing → must NOT sign.
        assertFalse(RelayAuthFirstParty.hasReason(me, relay, emptyList(), emptySet()))
    }

    @Test
    fun publishingSomeoneElsesEventIsNotFirstParty() {
        assertFalse(RelayAuthFirstParty.hasReason(me, relay, listOf(event(other)), emptySet()))
    }

    @Test
    fun aRelayIConfiguredIsFirstParty() {
        // Own inbox/outbox reads qualify this way: the relay serving them is in my own relay list.
        assertTrue(RelayAuthFirstParty.hasReason(me, relay, emptyList(), setOf(relay)))
    }

    @Test
    fun publishingMyOwnEventIsFirstParty() {
        // Delivering my own DM/post to the recipient's relay, even one not in my list.
        assertTrue(RelayAuthFirstParty.hasReason(me, relay, listOf(event(me)), emptySet()))
    }

    @Test
    fun aRelayHostingAJoinedGroupIsFirstParty() {
        // A NIP-29 group host relay is in none of my NIP-65/DM/… lists, and a private group's content
        // is `#h`-scoped so it never names me — the joined-group set is the only signal that lets us
        // AUTH so the relay serves the group's `auth-required` content instead of leaving it empty.
        val groupRelay = NormalizedRelayUrl("wss://chat.wisp.talk/")
        assertTrue(RelayAuthFirstParty.hasReason(me, groupRelay, emptyList(), emptySet(), setOf(groupRelay)))
    }

    @Test
    fun aGroupRelayIHaveNotJoinedIsNotFirstParty() {
        // Merely knowing a group relay exists (e.g. browsing its public directory) must not AUTH it;
        // only a group on my own kind-10009 list counts.
        val groupRelay = NormalizedRelayUrl("wss://chat.wisp.talk/")
        assertFalse(RelayAuthFirstParty.hasReason(me, groupRelay, emptyList(), emptySet(), emptySet()))
    }

    @Test
    fun aRelayHostingAJoinedConcordCommunityIsFirstParty() {
        // Concord is the harder case of the same rule: a plane wrap this account publishes is signed
        // by the plane's *stream key*, so even its own outbound traffic carries someone else's pubkey
        // and the pendingEvents rule can never fire. The joined-communities list is the only signal.
        val concordRelay = NormalizedRelayUrl("wss://relay.dreamith.to/")
        val planeWrap = event(other)
        assertTrue(RelayAuthFirstParty.hasReason(me, concordRelay, listOf(planeWrap), emptySet(), setOf(concordRelay)))
    }
}
