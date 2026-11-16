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
package com.vitorpamplona.amethyst.commons.service.broadcast

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastTrackerTest {
    private val event =
        Event(
            id = "a".padEnd(64, '0'),
            pubKey = "pub".padEnd(64, '0'),
            createdAt = 0L,
            kind = 1,
            tags = emptyArray(),
            content = "",
            sig = "sig".padEnd(128, '0'),
        )

    @Test
    fun anEventWithNoRelaysIsNotTracked() =
        runTest {
            val tracker = BroadcastTracker(outboxRelays = { emptySet() })

            tracker.trackBroadcast(event = event, relays = emptySet(), client = EmptyNostrClient())

            assertTrue(tracker.activeBroadcasts.value.isEmpty(), "nothing was sent, so there is nothing to report")
        }

    @Test
    fun aDroppedConnectionIsNotAFailureIfTheRelayLaterAccepts() =
        runTest {
            val client = ScriptedClient()
            val tracker = BroadcastTracker(outboxRelays = { setOf(outbox) })

            launch { tracker.trackBroadcast(event = event, relays = setOf(outbox), client = client) }
            runCurrent()

            // the connection drops right after publishing; the client resends on reconnect.
            client.listener!!.onDisconnected(FakeRelay(outbox))
            runCurrent()

            val afterDrop = tracker.activeBroadcasts.value.single()
            assertNull(afterDrop.results[outbox], "a dropped connection is not the relay's answer")
            assertFalse(afterDrop.needsAttention)

            advanceTimeBy(2_700)
            client.listener!!.onIncomingMessage(FakeRelay(outbox), "", OkMessage(event.id, true, ""))
            advanceUntilIdle()

            val done = tracker.activeBroadcasts.value.single()
            assertEquals(RelayResult.Success, done.results[outbox])
            assertTrue(done.isOut)
            assertFalse(done.needsAttention)
        }

    @Test
    fun aRelayThatNeverAnswersKeepsItsConnectionError() =
        runTest {
            val client = ScriptedClient()
            val tracker = BroadcastTracker(outboxRelays = { setOf(outbox) })

            launch { tracker.trackBroadcast(event = event, relays = setOf(outbox), client = client) }
            runCurrent()
            client.listener!!.onCannotConnect(FakeRelay(outbox), "HTTP 530")
            advanceUntilIdle()

            val done = tracker.activeBroadcasts.value.single()
            assertEquals(RelayResult.Error("HTTP 530"), done.results[outbox], "the details sheet still says why")
            assertTrue(done.needsAttention)
        }

    @Test
    fun aRetryAlsoWaitsPastADroppedConnection() =
        runTest {
            val client = ScriptedClient()
            val tracker = BroadcastTracker(outboxRelays = { setOf(outbox) })

            launch { tracker.trackBroadcast(event = event, relays = setOf(outbox), client = client) }
            runCurrent()
            client.listener!!.onCannotConnect(FakeRelay(outbox), "HTTP 530")
            advanceUntilIdle()

            launch { tracker.retry(tracker.activeBroadcasts.value.single(), client) }
            runCurrent()
            client.listener!!.onDisconnected(FakeRelay(outbox))
            advanceTimeBy(2_000)
            client.listener!!.onIncomingMessage(FakeRelay(outbox), "", OkMessage(event.id, true, ""))
            advanceUntilIdle()

            val done = tracker.activeBroadcasts.value.single()
            assertEquals(RelayResult.Success, done.results[outbox])
            assertFalse(done.needsAttention)
        }

    @Test
    fun aRateLimitWithABackOffShowsRetryingUntilTheResentEventLands() =
        runTest {
            val client = ScriptedClient()
            val tracker = BroadcastTracker(outboxRelays = { setOf(outbox) })

            launch { tracker.trackBroadcast(event = event, relays = setOf(outbox), client = client) }
            runCurrent()
            client.listener!!.onIncomingMessage(FakeRelay(outbox), "", OkMessage.rateLimited(event.id, "slow down", 1_000))
            runCurrent()

            val waiting = tracker.activeBroadcasts.value.single()
            assertEquals(RelayResult.Retrying, waiting.results[outbox], "the client resends after the back-off")
            assertFalse(waiting.needsAttention)

            advanceTimeBy(1_000)
            client.listener!!.onIncomingMessage(FakeRelay(outbox), "", OkMessage(event.id, true, ""))
            advanceUntilIdle()

            val done = tracker.activeBroadcasts.value.single()
            assertEquals(RelayResult.Success, done.results[outbox])
            assertTrue(done.isOut)
        }

    @Test
    fun aRateLimitWhoseRetryNeverAnswersKeepsTheRefusal() =
        runTest {
            val client = ScriptedClient()
            val tracker = BroadcastTracker(outboxRelays = { setOf(outbox) })

            launch { tracker.trackBroadcast(event = event, relays = setOf(outbox), client = client) }
            runCurrent()
            client.listener!!.onIncomingMessage(FakeRelay(outbox), "", OkMessage.rateLimited(event.id, "slow down", 1_000))
            advanceUntilIdle()

            val done = tracker.activeBroadcasts.value.single()
            assertEquals(RelayResult.Error("rate-limited: slow down"), done.results[outbox])
            assertTrue(done.needsAttention)
        }

    @Test
    fun aBackOffLongerThanTheWaitIsReportedAtOnce() =
        runTest {
            val client = ScriptedClient()
            val tracker = BroadcastTracker(outboxRelays = { setOf(outbox) })

            launch { tracker.trackBroadcast(event = event, relays = setOf(outbox), client = client) }
            runCurrent()
            client.listener!!.onIncomingMessage(FakeRelay(outbox), "", OkMessage.rateLimited(event.id, "slow down", 60_000))
            runCurrent()

            val done = tracker.activeBroadcasts.value.single()
            assertEquals(RelayResult.Error("rate-limited: slow down"), done.results[outbox])
            assertTrue(done.needsAttention)
        }

    private val outbox = NormalizedRelayUrl("wss://outbox.test/")

    private class ScriptedClient : INostrClient by EmptyNostrClient() {
        var listener: RelayConnectionListener? = null

        override fun addConnectionListener(listener: RelayConnectionListener) {
            this.listener = listener
        }

        override fun removeConnectionListener(listener: RelayConnectionListener) {
            if (this.listener == listener) this.listener = null
        }
    }

    private class FakeRelay(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        override fun connect() {}

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) {}

        override fun isConnected() = true

        override fun sendOrConnectAndSync(cmd: Command) {}

        override fun sendIfConnected(cmd: Command) {}

        override fun disconnect() {}
    }
}
