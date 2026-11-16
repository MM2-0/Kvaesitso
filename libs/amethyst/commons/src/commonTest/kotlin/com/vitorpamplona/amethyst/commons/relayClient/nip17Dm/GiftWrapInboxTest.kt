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
package com.vitorpamplona.amethyst.commons.relayClient.nip17Dm

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The account's single gift-wrap subscription picks the narrowest filter the enabled features need:
 * NIP-17 takes everything (DMs, calls, Welcomes, invites); without it, Marmot asks for plain kind-1059
 * wraps (Welcomes carry no distinguishing tag), and Concord alone asks for `k=3313` invites only.
 */
class GiftWrapInboxTest {
    private val me = "aa".repeat(32)
    private val relay = NormalizedRelayUrl("wss://inbox.example/")

    @Test
    fun nip17TakesEverythingWhateverElseIsOn() {
        for (marmot in listOf(true, false)) {
            for (concord in listOf(true, false)) {
                assertEquals(GiftWrapInbox.EVERYTHING, GiftWrapInbox.choose(nip17 = true, marmot = marmot, concord = concord))
            }
        }
    }

    @Test
    fun withoutNip17MarmotNeedsAllWrapsAndCoversConcordToo() {
        assertEquals(GiftWrapInbox.MARMOT_WELCOMES, GiftWrapInbox.choose(nip17 = false, marmot = true, concord = true))
        assertEquals(GiftWrapInbox.MARMOT_WELCOMES, GiftWrapInbox.choose(nip17 = false, marmot = true, concord = false))
    }

    @Test
    fun concordAloneOnlyAsksForInvites() {
        assertEquals(GiftWrapInbox.CONCORD_INVITES, GiftWrapInbox.choose(nip17 = false, marmot = false, concord = true))
        assertEquals(GiftWrapInbox.NONE, GiftWrapInbox.choose(nip17 = false, marmot = false, concord = false))
    }

    @Test
    fun marmotWelcomeFilterSkipsEphemeralCallWraps() {
        val since = 1_000_000L
        val filter = filterMarmotWelcomesToPubkey(relay, me, since).single()
        assertEquals(relay, filter.relay)
        assertEquals(listOf(GiftWrapEvent.KIND), filter.filter.kinds)
        assertFalse(EphemeralGiftWrapEvent.KIND in filter.filter.kinds.orEmpty())
        assertEquals(listOf(me), filter.filter.tags?.get("p"))
        // Same backdate widening as the NIP-17 filter.
        assertEquals(since - TimeUtils.twoDays(), filter.filter.since)
    }

    @Test
    fun concordInviteFilterOnlyAsksForTaggedInvites() {
        val filter = filterConcordDirectInvitesToPubkey(relay, me, since = null).single()
        assertEquals(listOf(GiftWrapEvent.KIND), filter.filter.kinds)
        assertEquals(listOf(me), filter.filter.tags?.get("p"))
        assertEquals(listOf("3313"), filter.filter.tags?.get("k"))
        assertNull(filter.filter.since)
    }
}
