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
package com.vitorpamplona.amethyst.commons.domain.nip46

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribe
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSwitchRelays
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.server.BunkerRequestProcessor
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46RequestAuthorizer
import com.vitorpamplona.quartz.nip46RemoteSigner.server.NostrConnectSignerService
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NIP-46 `switch_relays` and `logout` from the client side, against signers on in-process relays.
 */
class RemoteSignerSessionTest {
    private val relayA = NormalizedRelayUrl("wss://relay-a.example.com/")
    private val relayB = NormalizedRelayUrl("wss://relay-b.example.com/")

    private object NeverConnects : WebSocket {
        override fun needsReconnect() = false

        override fun connect() {}

        override fun disconnect() {}

        override fun send(msg: String) = false
    }

    private class AllowAuthorizer : Nip46RequestAuthorizer {
        val loggedOut = CopyOnWriteArrayList<HexKey>()

        override suspend fun isPaired(clientPubKey: HexKey) = true

        override suspend fun onConnect(
            clientPubKey: HexKey,
            request: BunkerRequestConnect,
        ) = Nip46ConnectDecision.Accept(request.secret ?: "ack")

        override suspend fun authorize(
            clientPubKey: HexKey,
            request: BunkerRequest,
        ) = true

        override suspend fun onLogout(clientPubKey: HexKey) {
            loggedOut.add(clientPubKey)
        }
    }

    private class Relays(
        val scope: CoroutineScope,
        val servers: Map<NormalizedRelayUrl, NostrServer>,
    ) {
        val client =
            NostrClient(
                object : WebsocketBuilder {
                    override fun build(
                        url: NormalizedRelayUrl,
                        out: WebSocketListener,
                    ) = servers[url]?.let { InProcessWebSocket(it, out) } ?: NeverConnects
                },
                scope,
            )

        fun close() {
            scope.cancel()
            servers.values.forEach { it.close() }
        }
    }

    private fun relays(vararg urls: NormalizedRelayUrl) = Relays(CoroutineScope(Dispatchers.Default + SupervisorJob()), urls.associateWith { NostrServer(EventStore(null)) })

    @Test
    fun logoutReachesTheSigner() =
        runBlocking(Dispatchers.Default) {
            val net = relays(relayA)
            val authorizer = AllowAuthorizer()
            val bunkerTransport = NostrSignerInternal(KeyPair())
            val processor = BunkerRequestProcessor(NostrSignerInternal(KeyPair()), { setOf(relayA) }, authorizer)
            val service = NostrConnectSignerService(net.client, bunkerTransport, processor, setOf(relayA))
            net.scope.launch(start = CoroutineStart.UNDISPATCHED) { service.run() }

            val transport = KeyPair()
            try {
                val acked =
                    withTimeout(20_000) {
                        RemoteSignerLogoutUseCase.execute(
                            NostrConnectURI.buildBunker(bunkerTransport.pubKey, setOf(relayA), secret = null),
                            transport.privKey!!.toHexKey(),
                            net.client,
                        )
                    }
                assertTrue(acked, "the signer acknowledged the logout")
                assertEquals(listOf(transport.pubKey.toHexKey()), authorizer.loggedOut.toList(), "the signer dropped this client's session")
            } finally {
                net.close()
            }
        }

    @Test
    fun aLogoutNoSignerHearsDoesNotHang() =
        runBlocking(Dispatchers.Default) {
            val net = relays()
            try {
                val acked =
                    withTimeout(RemoteSignerLogoutUseCase.LOGOUT_TIMEOUT_MS + 5_000) {
                        RemoteSignerLogoutUseCase.execute(
                            NostrConnectURI.buildBunker(KeyPair().pubKey.toHexKey(), setOf(relayA), secret = null),
                            KeyPair().privKey!!.toHexKey(),
                            net.client,
                        )
                    }
                assertTrue(!acked)
            } finally {
                net.close()
            }
        }

    @Test
    fun aSignerThatStaysKeepsItsRelays() =
        runBlocking(Dispatchers.Default) {
            val net = relays(relayA)
            val bunkerTransport = NostrSignerInternal(KeyPair())
            val processor = BunkerRequestProcessor(NostrSignerInternal(KeyPair()), { setOf(relayA) }, AllowAuthorizer())
            val service = NostrConnectSignerService(net.client, bunkerTransport, processor, setOf(relayA))
            net.scope.launch(start = CoroutineStart.UNDISPATCHED) { service.run() }

            val switched = CopyOnWriteArrayList<Set<NormalizedRelayUrl>>()
            val signer = NostrSignerRemote(NostrSignerInternal(KeyPair()), bunkerTransport.pubKey, setOf(relayA), net.client, onRelaysSwitched = { switched.add(it) })
            try {
                assertNull(withTimeout(20_000) { signer.switchRelays() })
                assertEquals(setOf(relayA), signer.relays)
                assertTrue(switched.isEmpty())
            } finally {
                signer.closeSubscription()
                net.close()
            }
        }

    /**
     * A request sent before the switch is still in flight on the old relay when the signer moves
     * the session: its answer, which comes back on the old relay, still arrives.
     */
    @Test
    fun anAnswerAlreadyOnItsWayOverTheOldRelayStillArrives() =
        runBlocking(Dispatchers.Default) {
            val net = relays(relayA, relayB)
            val bunkerTransport = NostrSignerInternal(KeyPair())
            val processor = BunkerRequestProcessor(NostrSignerInternal(KeyPair()), { setOf(relayB) }, AllowAuthorizer())

            val handle =
                net.client.subscribe(relayA, Filter(kinds = listOf(NostrConnectEvent.KIND), tags = mapOf("p" to listOf(bunkerTransport.pubKey)))) { event ->
                    if (event !is NostrConnectEvent) return@subscribe
                    net.scope.launch {
                        val app = event.talkingWith(bunkerTransport.pubKey)
                        val request = event.decryptMessage(bunkerTransport) as? BunkerRequest ?: return@launch
                        val response =
                            if (request is BunkerRequestSwitchRelays) {
                                BunkerResponse(request.id, """["${relayB.url}"]""", null)
                            } else {
                                // Slow: answered after the switch has moved the client.
                                delay(1_500)
                                processor.process(app, request)
                            }
                        net.client.publish(NostrConnectEvent.create(response, app, bunkerTransport), setOf(relayA))
                    }
                }

            val signer = NostrSignerRemote(NostrSignerInternal(KeyPair()), bunkerTransport.pubKey, setOf(relayA), net.client)
            try {
                val ping = net.scope.async { signer.ping() }
                delay(300)
                assertEquals(setOf(relayB), withTimeout(20_000) { signer.switchRelays() })
                val answered = withTimeout(20_000) { ping.await() }
                assertTrue(answered.isNotEmpty(), "answered over the relay the switch moved off")
            } finally {
                signer.closeSubscription()
                handle.close()
                net.close()
            }
        }

    /**
     * A signer that answers `switch_relays` with a new relay: the client moves its requests and its
     * response subscription there, and tells the caller so the address can be saved.
     */
    @Test
    fun aSignerThatMovesIsFollowed() =
        runBlocking(Dispatchers.Default) {
            val net = relays(relayA, relayB)
            val bunkerTransport = NostrSignerInternal(KeyPair())
            val processor = BunkerRequestProcessor(NostrSignerInternal(KeyPair()), { setOf(relayB) }, AllowAuthorizer())
            val arrivals = CopyOnWriteArrayList<Pair<String, NormalizedRelayUrl>>()

            // The signer listens on both relays and answers on the one each request came in on.
            val handles =
                listOf(relayA, relayB).map { relay ->
                    net.client.subscribe(relay, Filter(kinds = listOf(NostrConnectEvent.KIND), tags = mapOf("p" to listOf(bunkerTransport.pubKey)))) { event ->
                        if (event !is NostrConnectEvent) return@subscribe
                        net.scope.launch {
                            val app = event.talkingWith(bunkerTransport.pubKey)
                            val request = event.decryptMessage(bunkerTransport) as? BunkerRequest ?: return@launch
                            arrivals.add(request.method to relay)
                            val response =
                                if (request is BunkerRequestSwitchRelays) {
                                    BunkerResponse(request.id, """["${relayB.url}"]""", null)
                                } else {
                                    processor.process(app, request)
                                }
                            net.client.publish(NostrConnectEvent.create(response, app, bunkerTransport), setOf(relay))
                        }
                    }
                }

            val switched = CopyOnWriteArrayList<Set<NormalizedRelayUrl>>()
            val signer = NostrSignerRemote(NostrSignerInternal(KeyPair()), bunkerTransport.pubKey, setOf(relayA), net.client, onRelaysSwitched = { switched.add(it) })
            try {
                assertEquals(setOf(relayB), withTimeout(20_000) { signer.switchRelays() })
                assertEquals(setOf(relayB), signer.relays)
                assertEquals(listOf(setOf(relayB)), switched.toList())

                // Answered (rather than timing out) only if the response subscription moved to relay B as well.
                withTimeout(20_000) { signer.ping() }
                assertEquals(listOf("switch_relays" to relayA, "ping" to relayB), arrivals.toList())
            } finally {
                signer.closeSubscription()
                handles.forEach { it.close() }
                net.close()
            }
        }
}
