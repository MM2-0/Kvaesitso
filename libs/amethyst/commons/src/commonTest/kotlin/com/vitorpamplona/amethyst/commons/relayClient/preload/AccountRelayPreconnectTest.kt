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
package com.vitorpamplona.amethyst.commons.relayClient.preload

import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountRelayPreconnectTest {
    private val a = NormalizedRelayUrl("wss://a.example/")
    private val b = NormalizedRelayUrl("wss://b.example/")

    /** Counts the holds keepConnected/releaseConnection leave on each relay. */
    private class HoldingClient : INostrClient by EmptyNostrClient() {
        val holds = mutableMapOf<NormalizedRelayUrl, Int>()

        override fun keepConnected(url: NormalizedRelayUrl) {
            holds[url] = (holds[url] ?: 0) + 1
        }

        override fun releaseConnection(url: NormalizedRelayUrl) {
            holds[url] = (holds[url] ?: 0) - 1
        }

        fun held() = holds.filterValues { it > 0 }.keys
    }

    @Test
    fun holdsUntilTheTimeoutThenLetsGo() =
        runTest {
            val client = HoldingClient()
            AccountRelayPreconnect(client, this, holdMs = 1_000).hold(setOf(a, b), MutableStateFlow(emptySet()))
            runCurrent()
            assertEquals(setOf(a, b), client.held())

            advanceTimeBy(1_001)
            runCurrent()
            assertTrue(client.held().isEmpty())
            assertEquals(mapOf(a to 0, b to 0), client.holds)
        }

    @Test
    fun aRelayBlockedWhileHeldIsLetGoAtOnceAndOnlyOnce() =
        runTest {
            val client = HoldingClient()
            val blocked = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
            AccountRelayPreconnect(client, this, holdMs = 1_000).hold(setOf(a, b), blocked)
            runCurrent()

            // The blocked list arrives after the login.
            blocked.value = setOf(b)
            runCurrent()
            assertEquals(setOf(a), client.held())

            advanceTimeBy(1_001)
            runCurrent()
            // b was released once when blocked, not again at the timeout.
            assertEquals(mapOf(a to 0, b to 0), client.holds)
        }

    @Test
    fun nothingToHoldStartsNothing() =
        runTest {
            val client = HoldingClient()
            assertNull(AccountRelayPreconnect(client, this).hold(emptySet(), MutableStateFlow(emptySet())))
            assertTrue(client.holds.isEmpty())
        }
}
