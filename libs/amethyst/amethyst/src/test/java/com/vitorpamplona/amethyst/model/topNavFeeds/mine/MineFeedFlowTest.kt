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
package com.vitorpamplona.amethyst.model.topNavFeeds.mine

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.mine.MineFeedFlow
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MineFeedFlowTest {
    private val me = "00".repeat(32)
    private val someoneIFollow = "11".repeat(32)

    private val outbox = NormalizedRelayUrl("wss://outbox.example.com/")
    private val local = NormalizedRelayUrl("ws://127.0.0.1:4869/")
    private val proxy = NormalizedRelayUrl("wss://proxy.example.com/")

    @Test
    fun resolvesToAuthorFilterScopedToSelf_pinnedToTheGivenRelays() {
        val relays = setOf(outbox, local, proxy)
        val flow = MineFeedFlow(myPubkey = me, mineRelays = MutableStateFlow(relays))

        val filter = flow.startValue()

        assertTrue(filter is AuthorsByProxyTopNavFilter)
        filter as AuthorsByProxyTopNavFilter

        // Author scoped to the user; rejects a follow's posts.
        assertEquals(setOf(me), filter.authors)
        assertTrue(filter.matchAuthor(me))
        assertFalse(filter.matchAuthor(someoneIFollow))

        // Every "mine" relay is queried for the user's own events (no broadcast in this set).
        assertEquals(relays, filter.proxyRelays)
    }

    @Test
    fun emptyRelaySet_stillScopesAuthorButQueriesNothing() {
        val flow = MineFeedFlow(myPubkey = me, mineRelays = MutableStateFlow(emptySet()))

        val filter = flow.startValue() as AuthorsByProxyTopNavFilter

        // DAL still narrows to the user from cache even when no relays are configured.
        assertTrue(filter.matchAuthor(me))
        assertFalse(filter.matchAuthor(someoneIFollow))
        assertTrue(filter.proxyRelays.isEmpty())
    }

    @Test
    fun flow_reEmits_whenMineRelaysChange() =
        runTest {
            val relays = MutableStateFlow(setOf(outbox))
            val flow = MineFeedFlow(myPubkey = me, mineRelays = relays)

            val before = flow.flow().first() as AuthorsByProxyTopNavFilter
            assertEquals(setOf(outbox), before.proxyRelays)

            relays.value = setOf(outbox, local, proxy)
            val after = flow.flow().first() as AuthorsByProxyTopNavFilter
            assertEquals(setOf(outbox, local, proxy), after.proxyRelays)
            assertTrue(after.matchAuthor(me))
            assertFalse(after.matchAuthor(someoneIFollow))
        }
}
