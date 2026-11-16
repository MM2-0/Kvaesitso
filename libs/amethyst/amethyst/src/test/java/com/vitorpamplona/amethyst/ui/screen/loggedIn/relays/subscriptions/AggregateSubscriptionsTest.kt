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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.subscriptions

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.subscriptions.aggregateSubscriptions
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The unit invariant behind the Active Subscriptions screen: a purpose's filter count and the
 * screen's total must be the *same thing counted the same way*, because the card draws one as a
 * share of the other.
 *
 * It was broken by batching. A relay-group state filter carries every joined group on its host
 * relay, and a public-chat filter carries every followed chat — so summing the per-entity rows
 * counted one filter once per entity it named. Six chats over six relays reported 144 filters where
 * 24 were on the wire, and the resulting "8% of all" divided an inflated number by a real one.
 */
class AggregateSubscriptionsTest {
    private val account = "aa9047325603dacd4f8142093567973566de3b1e20a89557b728c3be4c6a844b"
    private val chats = (1..6).map { "cafe$it".padEnd(64, '0') }

    private fun relay(n: Int) = NormalizedRelayUrl("wss://relay$n.example/")

    /** One batched filter naming every chat the relay serves — what the real builders emit. */
    private fun batched(kind: Int) =
        ExplainedFilter(
            purpose = SubPurpose.PUBLIC_CHATS,
            entityIds = chats,
            kinds = listOf(kind),
            tags = mapOf("e" to chats),
            accountPubKeys = listOfNotNull(account),
        )

    /** Six relays, each carrying four batched filters that each name all six chats. */
    private fun sixChatsOnSixRelays(): Map<NormalizedRelayUrl, List<Filter>> = (1..6).associate { r -> relay(r) to listOf(batched(40), batched(41), batched(42), batched(43)) }

    @Test
    fun `a batched filter counts once, not once per entity it names`() {
        val state = aggregateSubscriptions(sixChatsOnSixRelays())

        // 6 relays x 4 filters = 24 actually in flight. The bug reported 144 (24 x 6 entities).
        assertEquals(24, state.totalFilters)

        val purpose =
            state.accounts
                .single()
                .purposes
                .single()
        assertEquals(24, purpose.filterCount)

        // The per-entity breakdown is still there, and still says each chat is named by 24 filters.
        assertEquals(6, purpose.entities.size)
        purpose.entities.forEach { assertEquals(24, it.namedInFilters) }
    }

    @Test
    fun `purpose counts sum to the total, so a share of the whole is a like-for-like ratio`() {
        val state = aggregateSubscriptions(sixChatsOnSixRelays())

        val summed = state.accounts.sumOf { acct -> acct.purposes.sumOf { it.filterCount } }
        assertEquals(state.totalFilters, summed)
        assertEquals(state.totalFilters, state.accounts.sumOf { it.filterCount })
    }

    @Test
    fun `relays are counted distinctly, not once per filter that reaches them`() {
        val state = aggregateSubscriptions(sixChatsOnSixRelays())

        assertEquals(6, state.totalRelays)
        assertEquals(
            6,
            state.accounts
                .single()
                .purposes
                .single()
                .relays.size,
        )
    }

    /** The merged notifications filter: one REQ per relay naming every account that reads it. */
    private fun mergedNotifications(vararg accounts: String) =
        ExplainedFilter(
            purpose = SubPurpose.NOTIFICATIONS,
            accountPubKeys = accounts.toList(),
            kinds = listOf(1),
            tags = mapOf("p" to accounts.toList()),
        )

    @Test
    fun `a merged filter explains itself to every account it serves`() {
        val a = "aa".repeat(32)
        val b = "bb".repeat(32)
        val c = "cc".repeat(32)
        val state = aggregateSubscriptions(mapOf(relay(1) to listOf(mergedNotifications(a, b, c))))

        // One filter on the wire...
        assertEquals(1, state.totalFilters)
        // ...but all three accounts can see why their relay is busy.
        assertEquals(3, state.accounts.size)
        state.accounts.forEach { assertEquals(1, it.filterCount) }
    }

    @Test
    fun `a card's share is drawn against the attributed total, not the wire total`() {
        val a = "aa".repeat(32)
        val b = "bb".repeat(32)
        val state = aggregateSubscriptions(mapOf(relay(1) to listOf(mergedNotifications(a, b))))

        // Dividing the per-account count by totalFilters would read as 100% for each of two
        // accounts. attributedFilters is the unit those counts actually belong to.
        assertEquals(1, state.totalFilters)
        assertEquals(2, state.attributedFilters)
        assertEquals(state.attributedFilters, state.accounts.sumOf { it.filterCount })
    }

    @Test
    fun `unmerged filters keep attributed and wire totals identical`() {
        val state = aggregateSubscriptions(sixChatsOnSixRelays())

        assertEquals(state.totalFilters, state.attributedFilters)
    }

    @Test
    fun `untagged filters are reported but never attributed to a purpose`() {
        val state =
            aggregateSubscriptions(
                mapOf(relay(1) to listOf(batched(42), Filter(kinds = listOf(1)))),
            )

        assertEquals(2, state.totalFilters)
        assertEquals(1, state.untaggedFilters)
        // Only the tagged one reaches a card, so the cards no longer add up to the total here —
        // which is exactly what the untagged line under the header exists to explain.
        assertEquals(1, state.accounts.sumOf { it.filterCount })
    }
}
