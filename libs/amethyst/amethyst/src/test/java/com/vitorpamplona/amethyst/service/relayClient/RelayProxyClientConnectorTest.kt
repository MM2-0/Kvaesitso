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

import com.vitorpamplona.amethyst.commons.relayClient.RelayProxyClientConnector
import com.vitorpamplona.amethyst.commons.relayClient.RelayProxyClientConnector.RelayServiceInfra
import com.vitorpamplona.amethyst.commons.service.connectivity.ConnectivityStatus
import com.vitorpamplona.amethyst.commons.tor.RelayClassification
import com.vitorpamplona.amethyst.commons.tor.TorRelayEvaluation
import com.vitorpamplona.amethyst.commons.tor.TorRelaySettings
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the decision table in [RelayProxyClientConnector.apply]: which environment changes
 * are allowed to forgive a relay's accumulated reconnect backoff.
 *
 * Before this was keyed on network identity, the only signal was OkHttpClient reference
 * identity, which is rebuilt off the metered bit. That misses every transition where
 * metered-ness does not flip — wifi A -> wifi B, a VPN coming up, a captive portal clearing,
 * metered wifi -> cellular — leaving relays parked on a backoff earned against a network the
 * device already left. It also missed a Tor policy flip while Tor was already up, where both
 * client references stay identical.
 */
class RelayProxyClientConnectorTest {
    /** Records what the connector asked the relay pool to do. */
    class RecordingClient : INostrClient by EmptyNostrClient() {
        var backoffResets = 0
        val reconnects = mutableListOf<Pair<Boolean, Boolean>>() // onlyIfChanged to ignoreRetryDelays
        var disconnects = 0
        private var active = false

        override fun isActive() = active

        override fun connect() {
            active = true
        }

        override fun disconnect() {
            active = false
            disconnects++
        }

        override fun resetBackoff() {
            backoffResets++
        }

        override fun reconnect(
            onlyIfChanged: Boolean,
            ignoreRetryDelays: Boolean,
        ) {
            reconnects.add(onlyIfChanged to ignoreRetryDelays)
        }

        /** True when the pool was told to tear everything down and rebuild. */
        fun sawFullRebuild() = reconnects.any { !it.first }

        fun sawBackoffForgiven() = backoffResets > 0 || sawFullRebuild()

        fun clear() {
            backoffResets = 0
            disconnects = 0
            reconnects.clear()
        }
    }

    // Only reference identity matters, and building a real OkHttpClient pulls in
    // android.util.Log, which is not usable in JVM unit tests.
    private val torClient = mockk<OkHttpClient>()
    private val clearClient = mockk<OkHttpClient>()

    private val client = RecordingClient()

    private val connector =
        RelayProxyClientConnector(
            torEvaluator = MutableStateFlow(evaluation()),
            torConnection = MutableStateFlow(torClient),
            clearConnection = MutableStateFlow(clearClient),
            connectivityStatus = MutableStateFlow(ConnectivityStatus.Off),
            torBootstrapped = MutableStateFlow(false),
            client = client,
            // The flow itself is never collected here; apply() is driven directly.
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

    private fun evaluation(settings: TorRelaySettings = TorRelaySettings()) =
        TorRelayEvaluation(
            torSettings = settings,
            classification =
                RelayClassification(
                    trusted = emptySet(),
                    dm = emptySet(),
                ),
        )

    private fun infra(
        networkId: Long = 1L,
        isMobile: Boolean = false,
        tor: OkHttpClient = torClient,
        clear: OkHttpClient = clearClient,
        evaluation: TorRelayEvaluation = evaluation(),
        torBootstrapped: Boolean = false,
    ) = RelayServiceInfra(
        evaluator = evaluation,
        torConnection = tor,
        clearConnection = clear,
        connectivity = ConnectivityStatus.Active(networkId, isMobile),
        torBootstrapped = torBootstrapped,
    )

    /**
     * Runs the first Active event — which activates the client and records the baseline
     * network and transport — then clears the recording so each test observes only its
     * own transition.
     */
    private fun settleOnFirstNetwork() {
        connector.apply(infra(networkId = 1L))
        client.clear()
    }

    @Test
    fun `a new network forgives the backoff even when metered-ness does not change`() {
        settleOnFirstNetwork()

        // metered wifi -> cellular: same isMobile, so the OkHttp clients are NOT rebuilt.
        connector.apply(infra(networkId = 2L))

        assertTrue(
            "A different network must forgive backoffs earned on the previous one",
            client.sawBackoffForgiven(),
        )
        assertTrue(
            "Sockets bound to the old interface are dead; the pool must be rebuilt, not " +
                "merely asked to reconnect what needsToReconnect() can see",
            client.sawFullRebuild(),
        )
    }

    @Test
    fun `flipping a tor toggle while tor is already up forgives the backoff`() {
        settleOnFirstNetwork()

        // Same network, same OkHttpClient instances: only the routing policy changed.
        connector.apply(
            infra(
                networkId = 1L,
                evaluation = evaluation(TorRelaySettings(torType = TorType.INTERNAL, dmRelaysViaTor = true)),
            ),
        )

        assertEquals(
            "A relay whose transport just flipped must not wait out a backoff earned on the other transport",
            1,
            client.backoffResets,
        )
        assertEquals(listOf(true to true), client.reconnects)
    }

    /**
     * The handover the guessed-relay feature exists to perform: when a user's own lists arrive, the
     * relays we were guessing about must re-dial onto whatever policy the user actually chose.
     *
     * The case that matters is an **empty** arriving list. Normally `trusted` grows at the same
     * moment and would have flagged the change on its own — but an account that publishes an empty
     * relay list leaves `trusted` untouched while `assumed` empties, and before the classification
     * was compared as one value that combination produced no reconnect at all, stranding those
     * relays on clearnet against a policy that had already moved them to Tor.
     */
    @Test
    fun `guessed relays re-dial when an empty list arrives and trusted does not change`() {
        settleOnFirstNetwork()

        val guessing =
            TorRelayEvaluation(
                torSettings = TorRelaySettings(),
                classification = RelayClassification(assumed = setOf(NormalizedRelayUrl("wss://guessed.example/"))),
            )
        connector.apply(infra(networkId = 1L, evaluation = guessing))
        client.reconnects.clear()

        // The user's own list arrives and is empty: `assumed` empties, `trusted` stays empty.
        val released =
            TorRelayEvaluation(
                torSettings = TorRelaySettings(),
                classification = RelayClassification(),
            )
        connector.apply(infra(networkId = 1L, evaluation = released))

        assertEquals(
            "The relays we stopped guessing about must be asked to re-dial onto their real policy",
            listOf(true to true),
            client.reconnects,
        )
    }

    @Test
    fun `unrelated churn on the same network leaves the backoff alone`() {
        settleOnFirstNetwork()

        // Tor bootstrap progress: nothing about the transport or the network changed.
        connector.apply(infra(networkId = 1L, torBootstrapped = true))

        assertEquals(
            "Backoff must survive unrelated infrastructure events, or dead relays get hammered",
            0,
            client.backoffResets,
        )
        assertTrue("No full rebuild for noise", !client.sawFullRebuild())
        assertEquals(listOf(true to false), client.reconnects)
    }

    @Test
    fun `a rebuilt transport on the same network forgives the backoff`() {
        settleOnFirstNetwork()

        // Tor's SOCKS port came up: the Tor-routed OkHttpClient is a new instance.
        connector.apply(infra(networkId = 1L, tor = mockk<OkHttpClient>()))

        assertEquals(1, client.backoffResets)
        assertEquals(listOf(true to true), client.reconnects)
    }

    /**
     * Losing connectivity already resets every relay through disconnect(), and coming back
     * dials the whole pool through connect(). Treating the new network as a change on top of
     * that would tear down a pool that was just rebuilt.
     */
    @Test
    fun `regaining connectivity on a different network does not double-rebuild`() {
        settleOnFirstNetwork()

        connector.apply(
            RelayServiceInfra(evaluation(), torClient, clearClient, ConnectivityStatus.Off, false),
        )
        assertEquals("Losing the network must pause the pool", 1, client.disconnects)
        client.clear()

        // back online, on a different network than the one we lost.
        connector.apply(infra(networkId = 7L))

        assertTrue(
            "connect() already dials every relay from a clean backoff; no rebuild needed",
            !client.sawFullRebuild(),
        )
        assertEquals(0, client.backoffResets)
    }
}
