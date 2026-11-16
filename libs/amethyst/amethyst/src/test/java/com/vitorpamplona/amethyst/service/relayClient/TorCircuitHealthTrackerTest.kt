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
package com.vitorpamplona.amethyst.service.relayClient

import com.vitorpamplona.amethyst.commons.relays.health.TorCircuitHealthTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TorCircuitHealthTracker]'s discriminator. Drives the captured
 * [RelayConnectionListener] directly with a virtual clock — no Arti, no real relay client.
 */
class TorCircuitHealthTrackerTest {
    private val torRelay = NormalizedRelayUrl("wss://torrelay.example/")
    private val clearRelay = NormalizedRelayUrl("wss://clearrelay.example/")

    /**
     * Drives a sustained, threshold-meeting failure streak: spreads count-1 failures in small
     * continuous steps, then jumps the clock so the final failure pushes the span just past the
     * floor — guaranteeing both the count and the SUSTAINED_MS span are met.
     */
    private fun Harness.sustainedFailures(count: Int = TorCircuitHealthTracker.FAIL_THRESHOLD) {
        val start = now
        repeat(count - 1) {
            failTor()
            now += 1_000 // small continuous gaps, well under SUSTAINED_MS
        }
        now = start + TorCircuitHealthTracker.SUSTAINED_MS + 1
        failTor()
    }

    @Test
    fun `fires on a sustained threshold-meeting failure streak`() {
        val h = Harness()
        h.sustainedFailures()
        assertEquals(1, h.deadCount)
    }

    @Test
    fun `does not fire on a short burst even past threshold`() {
        val h = Harness()
        // 8+ failures but all within ~2s — the post-Active warmup burst that wrongly wiped the
        // good client on device. Must NOT fire: the streak hasn't spanned SUSTAINED_MS.
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD * 2) {
            h.failTor()
            h.now += 200
        }
        assertEquals("a short burst is warmup, not a dead transport", 0, h.deadCount)
    }

    @Test
    fun `does not fire below threshold even when sustained`() {
        val h = Harness()
        // Spread a few failures across well over the span, but never reach the count.
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD - 1) {
            h.failTor()
            h.now += TorCircuitHealthTracker.SUSTAINED_MS
        }
        assertEquals(0, h.deadCount)
    }

    @Test
    fun `a single Tor success ends the streak`() {
        val h = Harness()
        h.sustainedFailures(count = TorCircuitHealthTracker.FAIL_THRESHOLD - 1)
        h.succeedTor() // warmup completed — streak ends
        h.failTor()
        assertEquals("one success means circuits work — suppress", 0, h.deadCount)
    }

    @Test
    fun `clearnet relay outcomes are ignored`() {
        val h = Harness()
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD * 4) {
            h.failClear()
            h.now += TorCircuitHealthTracker.SUSTAINED_MS
        }
        assertEquals(0, h.deadCount)
    }

    @Test
    fun `does not fire when connectivity is down`() {
        val h = Harness(connectivityActive = false)
        h.sustainedFailures()
        assertEquals("general outage must not reset Tor", 0, h.deadCount)
    }

    @Test
    fun `does not fire when Tor is not Active`() {
        val h = Harness(torActive = false)
        h.sustainedFailures()
        assertEquals(0, h.deadCount)
    }

    @Test
    fun `a long gap restarts the streak`() {
        val h = Harness()
        // Almost reach the count, sustained...
        repeat(TorCircuitHealthTracker.FAIL_THRESHOLD - 1) {
            h.failTor()
            h.now += 3_000
        }
        // ...then a gap longer than the span: the next failure starts a brand-new streak, so a
        // single fresh failure can't reach threshold.
        h.now += TorCircuitHealthTracker.SUSTAINED_MS + 1
        h.failTor()
        assertEquals(0, h.deadCount)
    }

    @Test
    fun `re-arms after firing — needs a fresh sustained streak to fire again`() {
        val h = Harness()
        h.sustainedFailures()
        assertEquals(1, h.deadCount)

        // Immediately after firing the streak is reset, so the next failure can't re-fire until a
        // new streak builds count AND spans the floor again.
        h.failTor()
        assertEquals(1, h.deadCount)

        h.now += TorCircuitHealthTracker.SUSTAINED_MS + 1 // gap resets, then a fresh sustained streak
        h.sustainedFailures()
        assertEquals(2, h.deadCount)
    }

    // ------------------------------------------------------------------
    // harness
    // ------------------------------------------------------------------

    private inner class Harness(
        torActive: Boolean = true,
        connectivityActive: Boolean = true,
    ) {
        var now: Long = 1_000_000_000_000L
        var deadCount: Int = 0
            private set

        private val client = CapturingClient()

        init {
            TorCircuitHealthTracker(
                client = client,
                isTorRouted = { it == torRelay },
                isTorActive = { torActive },
                isConnectivityActive = { connectivityActive },
                onCircuitsDead = { deadCount++ },
                nowMs = { now },
            ).register()
        }

        fun failTor() = client.listener!!.onCannotConnect(relayClient(torRelay), "SOCKS: Connection refused")

        fun failClear() = client.listener!!.onCannotConnect(relayClient(clearRelay), "boom")

        fun succeedTor() = client.listener!!.onConnected(relayClient(torRelay), 400, false)
    }

    private fun relayClient(relayUrl: NormalizedRelayUrl): IRelayClient =
        object : IRelayClient {
            override val url = relayUrl

            override fun connect() = error("unused")

            override fun needsToReconnect() = error("unused")

            override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = error("unused")

            override fun isConnected() = error("unused")

            override fun sendOrConnectAndSync(cmd: Command) = error("unused")

            override fun sendIfConnected(cmd: Command) = error("unused")

            override fun disconnect() = error("unused")
        }

    /** Minimal [INostrClient] (delegating to [EmptyNostrClient]) that just captures the listener. */
    private class CapturingClient(
        private val delegate: INostrClient = EmptyNostrClient(),
    ) : INostrClient by delegate {
        var listener: RelayConnectionListener? = null

        override fun addConnectionListener(listener: RelayConnectionListener) {
            this.listener = listener
        }

        override fun removeConnectionListener(listener: RelayConnectionListener) {
            if (this.listener === listener) this.listener = null
        }
    }
}
