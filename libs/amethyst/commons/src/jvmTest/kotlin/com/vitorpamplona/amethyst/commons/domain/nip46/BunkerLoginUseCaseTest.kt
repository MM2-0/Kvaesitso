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
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.server.BunkerRequestProcessor
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46RequestAuthorizer
import com.vitorpamplona.quartz.nip46RemoteSigner.server.NostrConnectSignerService
import kotlinx.coroutines.CancellationException
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Logging in with a `bunker://` address, against a real NIP-46 signer on an in-process relay.
 */
class BunkerLoginUseCaseTest {
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")

    private object NeverConnects : WebSocket {
        override fun needsReconnect() = false

        override fun connect() {}

        override fun disconnect() {}

        override fun send(msg: String) = false
    }

    private class AllowAuthorizer : Nip46RequestAuthorizer {
        var connectSecret: String? = null

        override suspend fun isPaired(clientPubKey: HexKey) = true

        override suspend fun onConnect(
            clientPubKey: HexKey,
            request: BunkerRequestConnect,
        ): Nip46ConnectDecision {
            connectSecret = request.secret
            return Nip46ConnectDecision.Accept(request.secret ?: "ack")
        }

        override suspend fun authorize(
            clientPubKey: HexKey,
            request: BunkerRequest,
        ) = true

        override suspend fun onLogout(clientPubKey: HexKey) {}
    }

    /**
     * Opal (and anything built on rust-nostr's `url` crate) percent-encodes the relay. Read raw,
     * `wss%3A%2F%2F…` named no relay Amethyst could reach, so it never sent the signer a request.
     */
    @Test
    fun logsInWithPercentEncodedBunkerAddress() =
        runBlocking(Dispatchers.Default) {
            val server = NostrServer(EventStore(null))
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            // Only the relay's real address reaches it, as on a network.
            val client =
                NostrClient(
                    object : WebsocketBuilder {
                        override fun build(
                            url: NormalizedRelayUrl,
                            out: WebSocketListener,
                        ) = if (url == relay) InProcessWebSocket(server, out) else NeverConnects
                    },
                    scope,
                )

            val user = NostrSignerInternal(KeyPair())
            val bunkerTransport = NostrSignerInternal(KeyPair())
            val authorizer = AllowAuthorizer()
            val processor = BunkerRequestProcessor(user, { setOf(relay) }, authorizer)
            val service = NostrConnectSignerService(client, bunkerTransport, processor, setOf(relay))
            scope.launch(start = CoroutineStart.UNDISPATCHED) { service.run() }

            // The shape Opal's `bunker_uri` prints.
            val bunkerUri = "bunker://${bunkerTransport.pubKey}?relay=wss%3A%2F%2Frelay.example.com&secret=0c7a1e"

            try {
                val result =
                    withTimeout(20_000) {
                        BunkerLoginUseCase.execute(bunkerUri, NostrSignerInternal(KeyPair()), client)
                    }
                result.signer.closeSubscription()

                assertEquals(user.pubKey, result.pubKeyHex)
                assertEquals("0c7a1e", authorizer.connectSecret, "the pairing secret reaches the signer")
            } finally {
                scope.cancel()
                server.close()
            }
        }

    /**
     * NIP-46 auth challenge, the way a web signer answers a `connect` it wants the user to confirm on
     * its page: `auth_url` first, then the real answer under the same request id once the user
     * has. The login shows the page's address and carries on with the answer that follows.
     */
    @Test
    fun authUrlChallengeIsShownAndTheLoginWaitsForTheRealAnswer() =
        runBlocking(Dispatchers.Default) {
            val server = NostrServer(EventStore(null))
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client =
                NostrClient(
                    object : WebsocketBuilder {
                        override fun build(
                            url: NormalizedRelayUrl,
                            out: WebSocketListener,
                        ) = if (url == relay) InProcessWebSocket(server, out) else NeverConnects
                    },
                    scope,
                )

            val user = NostrSignerInternal(KeyPair())
            val bunkerTransport = NostrSignerInternal(KeyPair())
            val processor = BunkerRequestProcessor(user, { setOf(relay) }, AllowAuthorizer())
            val authPage = "https://signer.example/confirm?token=abc"

            // A signer that challenges every connect before answering it.
            val bunker =
                client.subscribe(relay, Filter(kinds = listOf(NostrConnectEvent.KIND), tags = mapOf("p" to listOf(bunkerTransport.pubKey)))) { event ->
                    if (event !is NostrConnectEvent) return@subscribe
                    scope.launch {
                        val app = event.talkingWith(bunkerTransport.pubKey)
                        val request = event.decryptMessage(bunkerTransport) as? BunkerRequest ?: return@launch
                        if (request is BunkerRequestConnect) {
                            client.publish(NostrConnectEvent.create(BunkerResponse(request.id, BunkerResponse.RESULT_AUTH_URL, authPage), app, bunkerTransport), setOf(relay))
                            // The user confirming on the page.
                            delay(500)
                        }
                        client.publish(NostrConnectEvent.create(processor.process(app, request), app, bunkerTransport), setOf(relay))
                    }
                }

            val prompts = RemoteSignerAuthPrompts()
            val shown = CopyOnWriteArrayList<String>()
            val watcher = scope.launch { prompts.pending.collect { pages -> pages.forEach { if (it.url !in shown) shown.add(it.url) } } }
            try {
                val result =
                    withTimeout(20_000) {
                        BunkerLoginUseCase.execute(
                            "bunker://${bunkerTransport.pubKey}?relay=wss://relay.example.com",
                            NostrSignerInternal(KeyPair()),
                            client,
                            authPrompts = prompts,
                        )
                    }
                result.signer.closeSubscription()

                assertEquals(user.pubKey, result.pubKeyHex)
                assertEquals(emptyList(), prompts.pending.value, "the page comes down once its request is answered")
                assertEquals(listOf(authPage), shown, "the page was shown while the signer waited")
            } finally {
                watcher.cancel()
                bunker.close()
                scope.cancel()
                server.close()
            }
        }

    @Test
    fun anAddressWithoutRelaysFailsTheLogin() =
        runBlocking(Dispatchers.Default) {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client =
                NostrClient(
                    object : WebsocketBuilder {
                        override fun build(
                            url: NormalizedRelayUrl,
                            out: WebSocketListener,
                        ) = NeverConnects
                    },
                    scope,
                )
            try {
                BunkerLoginUseCase.execute("bunker://${KeyPair().pubKey.toHexKey()}?secret=s1", NostrSignerInternal(KeyPair()), client)
                fail("Login should fail without a relay")
            } catch (e: Exception) {
                assertFalse(e is CancellationException, e.toString())
            } finally {
                scope.cancel()
            }
        }

    /**
     * Anyone who sees the code can answer its key. A reply without the code's secret used to be
     * taken as the signer, which then received the account's encryption requests; it must be
     * ignored, and the real signer that answers with the secret still logs in.
     */
    @Test
    fun nostrConnectIgnoresAnAnswerWithoutTheSecret() =
        runBlocking(Dispatchers.Default) {
            val server = NostrServer(EventStore(null))
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client =
                NostrClient(
                    object : WebsocketBuilder {
                        override fun build(
                            url: NormalizedRelayUrl,
                            out: WebSocketListener,
                        ) = if (url == relay) InProcessWebSocket(server, out) else NeverConnects
                    },
                    scope,
                )

            val user = NostrSignerInternal(KeyPair())
            val signerTransport = NostrSignerInternal(KeyPair())
            val processor = BunkerRequestProcessor(user, { setOf(relay) }, AllowAuthorizer())
            val service = NostrConnectSignerService(client, signerTransport, processor, setOf(relay))
            scope.launch(start = CoroutineStart.UNDISPATCHED) { service.run() }

            val code = NostrConnectLoginUseCase.generateUri(KeyPair(), listOf(relay.url), BunkerClientMetadata(name = "Amethyst"))
            val codeKey = code.ephemeralKeyPair.pubKey.toHexKey()

            try {
                val login = async { NostrConnectLoginUseCase.awaitAndLogin(code, client) }
                // The code's subscription is open before anyone answers: these events are ephemeral.
                delay(1_000)

                val forger = NostrSignerInternal(KeyPair())
                client.publish(NostrConnectEvent.create(BunkerResponse("1", "ack", null), codeKey, forger), setOf(relay))
                delay(300)
                client.publish(NostrConnectEvent.create(BunkerResponse("2", code.secret, null), codeKey, signerTransport), setOf(relay))

                val result = withTimeout(20_000) { login.await() }
                result.signer.closeSubscription()

                assertEquals(user.pubKey, result.pubKeyHex, "the signer that proved the secret logged in")
            } finally {
                scope.cancel()
                server.close()
            }
        }

    /** The address a nostrconnect login stores survives a relay with characters a query reserves. */
    @Test
    fun nostrConnectCodeRoundTripsReservedCharacters() {
        val data = NostrConnectLoginUseCase.generateUri(KeyPair(), listOf("wss://relay.example.com/inbox?a=1&b=2"), BunkerClientMetadata(name = "Amethyst"))
        val parsed = NostrConnectURI.parseNostrConnect(data.uri)!!
        assertEquals(data.relays, parsed.relays)
        assertEquals("Amethyst", parsed.name)
    }

    /**
     * A relay that never connects used to end the login with a TimeoutCancellationException, which
     * callers rethrow as a cancellation: no error reached the screen and it spun forever.
     */
    @Test
    fun unreachableRelayFailsWithAnError() =
        runBlocking(Dispatchers.Default) {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val neverConnects =
                NostrClient(
                    object : WebsocketBuilder {
                        override fun build(
                            url: NormalizedRelayUrl,
                            out: WebSocketListener,
                        ) = NeverConnects
                    },
                    scope,
                )

            try {
                BunkerLoginUseCase.execute(
                    "bunker://${KeyPair().pubKey.toHexKey()}?relay=wss%3A%2F%2Fdown.example.com",
                    NostrSignerInternal(KeyPair()),
                    neverConnects,
                )
                fail("Login should fail when no relay connects")
            } catch (e: Exception) {
                assertFalse(e is CancellationException, "a timeout must not read as a cancellation: $e")
                assertTrue(e.message.orEmpty().contains("down.example.com"), e.message)
            } finally {
                scope.cancel()
            }
        }
}
