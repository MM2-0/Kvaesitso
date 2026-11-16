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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.buzz

import com.vitorpamplona.amethyst.commons.model.buzz.MembershipNotificationKinds
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.buzz.filterWorkspaceInboxToPubkey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The always-on workspace-inbox filter: what a Buzz relay addresses to me personally. Pins the `#p`
 * scope, the kind set, and — most importantly — that the filter carries NO `#h`, because this is the
 * query that discovers which channels exist for me in the first place.
 */
class FilterWorkspaceInboxToPubkeyTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://buzz.example.team/")!!
    private val me = "a".repeat(64)

    @Test
    fun `builds a single p-scoped filter over the workspace inbox kinds`() {
        val filters = filterWorkspaceInboxToPubkey(relay, me, since = 500L)

        val f = filters.single()
        assertEquals(relay, f.relay)
        assertEquals(listOf(me), f.filter.tags!!["p"])
        assertEquals(500L, f.filter.since)
        assertNull(f.filter.until)
        assertNull(f.filter.authors)
    }

    @Test
    fun `carries no channel scope`() {
        // A `#h` here would be a contradiction: the channel ids are what this query is FOR. It also has
        // to stay off any subscription that does carry one — buzz downgrades a mixed subscription to
        // "global", which never receives channel-scoped events.
        val f = filterWorkspaceInboxToPubkey(relay, me, since = null).single()

        assertNull(f.filter.tags!!["h"])
        assertEquals(setOf("p"), f.filter.tags!!.keys)
    }

    @Test
    fun `asks for both membership verdicts and the hidden-DM snapshot`() {
        val kinds = filterWorkspaceInboxToPubkey(relay, me, since = null).single().filter.kinds!!

        assertTrue(kinds.contains(44100)) // MemberAddedNotificationEvent
        assertTrue(kinds.contains(44101)) // MemberRemovedNotificationEvent — withdraws an add
        assertTrue(kinds.contains(30622)) // DmVisibilityEvent — which DMs I hid
    }

    @Test
    fun `the removal kind travels with the add kind`() {
        // The invite projection resolves each channel to its NEWEST verdict, so asking for adds without
        // removals would leave a prompt standing for a membership the relay already took away.
        assertTrue(MembershipNotificationKinds.contains(44100))
        assertTrue(MembershipNotificationKinds.contains(44101))
    }

    @Test
    fun `no pubkey produces no filter`() {
        assertTrue(filterWorkspaceInboxToPubkey(relay, null, since = null).isEmpty())
        assertTrue(filterWorkspaceInboxToPubkey(relay, "", since = null).isEmpty())
    }
}
