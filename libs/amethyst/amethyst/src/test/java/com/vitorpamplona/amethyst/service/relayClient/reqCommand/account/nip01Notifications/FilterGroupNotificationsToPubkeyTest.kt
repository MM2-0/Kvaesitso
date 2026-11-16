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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip01Notifications

import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.GroupNotificationKinds
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterGroupNotificationsToPubkey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The always-on group-notifications filter: content in NIP-29 groups that `p`-tags me, so a reply/mention
 * reaches my notifications even for a group I never opened. Pins the `#p`+`#h` scope, the notification kind
 * set, and the empty-guards (no pubkey / no groups ⇒ no REQ at all).
 */
class FilterGroupNotificationsToPubkeyTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay-a.example/")!!
    private val me = "a".repeat(64)

    @Test
    fun `builds a single host p-and-h scoped filter over the notification kinds`() {
        val filters = filterGroupNotificationsToPubkey(relay, me, listOf("g1", "g2"), since = 500L)

        val f = filters.single()
        assertEquals(relay, f.relay)
        assertEquals(GroupNotificationKinds, f.filter.kinds)
        assertEquals(listOf(me), f.filter.tags!!["p"])
        assertEquals(listOf("g1", "g2"), f.filter.tags!!["h"])
        assertEquals(200, f.filter.limit)
        assertEquals(500L, f.filter.since)
        assertNull(f.filter.until)
        assertNull(f.filter.authors)
    }

    @Test
    fun `notification kinds include chat, comments, reactions, reposts and zaps`() {
        // A mention can arrive as any of these; the set must not silently drop one.
        assertTrue(GroupNotificationKinds.contains(9)) // ChatEvent
        assertTrue(GroupNotificationKinds.contains(1111)) // CommentEvent
        assertTrue(GroupNotificationKinds.contains(7)) // ReactionEvent
        assertTrue(GroupNotificationKinds.contains(9735)) // ZapReceiptEvent
    }

    @Test
    fun `no pubkey or no groups produces no filter`() {
        assertTrue(filterGroupNotificationsToPubkey(relay, null, listOf("g1"), since = null).isEmpty())
        assertTrue(filterGroupNotificationsToPubkey(relay, "", listOf("g1"), since = null).isEmpty())
        assertTrue(filterGroupNotificationsToPubkey(relay, me, emptyList(), since = null).isEmpty())
    }
}
