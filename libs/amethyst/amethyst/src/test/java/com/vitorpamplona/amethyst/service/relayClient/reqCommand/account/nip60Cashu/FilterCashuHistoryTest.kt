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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip60Cashu

import com.vitorpamplona.amethyst.commons.relayClient.account.nip60Cashu.filterCashuHistoryToPubkey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the backward-paging Cashu history filter: it must ask for the N newest kind:7376 rows I authored
 * strictly OLDER than a cursor (`until`+`limit`, no `since`), so the single per-relay cursor the
 * [BackwardRelayPager][com.vitorpamplona.amethyst.commons.relayClient.paging.BackwardRelayPager] tracks
 * can't skip a band and an empty page truly means "nothing older" (see RelayLoadingCursors).
 */
class FilterCashuHistoryTest {
    private val relay = RelayUrlNormalizer.normalize("wss://outbox.example.com")
    private val pubkey = "aa".repeat(32)
    private val until = 1_700_000_000L

    @Test
    fun `history filter asks one until+limit page of my own rows, no since`() {
        val filters = filterCashuHistoryToPubkey(relay, pubkey, until, 100)

        assertEquals(1, filters.size)
        val f = filters.first().filter
        assertEquals(relay, filters.first().relay)
        assertEquals(until, f.until)
        assertEquals(100, f.limit)
        assertNull("history pages by until, never since", f.since)
        // Own events are read back by author, not by a #p tag — unlike notifications, these are mine.
        assertEquals(listOf(pubkey), f.authors)
    }

    @Test
    fun `history filter is scoped to kind 7376 alone`() {
        val f = filterCashuHistoryToPubkey(relay, pubkey, until, 100).first().filter

        assertEquals(listOf(CashuSpendingHistoryEvent.KIND), f.kinds)
        // Proofs must never be paged on demand: a balance summed over a partial kind:7375 set is wrong,
        // not merely incomplete, so those are walked to exhaustion by CashuWalletState instead.
        assertTrue(
            "kind:7375 must not ride along on a demand-paged query",
            CashuTokenEvent.KIND !in f.kinds.orEmpty(),
        )
    }

    @Test
    fun `empty pubkey yields no filter`() {
        assertTrue(filterCashuHistoryToPubkey(relay, null, until, 100).isEmpty())
        assertTrue(filterCashuHistoryToPubkey(relay, "", until, 100).isEmpty())
    }
}
