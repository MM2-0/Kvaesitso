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
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05RemoteSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.server.BunkerRequestProcessor
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46RequestAuthorizer
import com.vitorpamplona.quartz.nip46RemoteSigner.server.NostrConnectSignerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Signing login from a NIP-05 address through the signer its domain advertises, against a real
 * NIP-46 signer on an in-process relay.
 */
class Nip05SignerLoginUseCaseTest {
    private val relay = NormalizedRelayUrl("wss://relay.signer.example/")

    private object NeverConnects : WebSocket {
        override fun needsReconnect() = false

        override fun connect() {}

        override fun disconnect() {}

        override fun send(msg: String) = false
    }

    private object AllowAll : Nip46RequestAuthorizer {
        override suspend fun isPaired(clientPubKey: HexKey) = true

        override suspend fun onConnect(
            clientPubKey: HexKey,
            request: BunkerRequestConnect,
        ) = Nip46ConnectDecision.Accept(request.secret ?: "ack")

        override suspend fun authorize(
            clientPubKey: HexKey,
            request: BunkerRequest,
        ) = true

        override suspend fun onLogout(clientPubKey: HexKey) {}
    }

    private class Net(
        relay: NormalizedRelayUrl,
    ) {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val server = NostrServer(EventStore(null))
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

        /** A signer for [user] that listens and answers as [transport]. */
        fun runSigner(
            user: NostrSignerInternal,
            transport: NostrSignerInternal,
            relays: Set<NormalizedRelayUrl>,
        ) {
            val service = NostrConnectSignerService(client, transport, BunkerRequestProcessor(user, { relays }, AllowAll), relays)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { service.run() }
        }

        fun close() {
            scope.cancel()
            server.close()
        }
    }

    /** Legacy shape: the address's key is the signer, reached with no secret. */
    @Test
    fun legacySignerLogsInAsTheAddress() =
        runBlocking(Dispatchers.Default) {
            val net = Net(relay)
            val user = NostrSignerInternal(KeyPair())
            net.runSigner(user, user, setOf(relay))
            try {
                val login =
                    withTimeout(20_000) {
                        Nip05SignerLoginUseCase.execute(Nip05RemoteSigner(user.pubKey, user.pubKey, setOf(relay)), KeyPair(), net.client)
                    }
                login.result.signer.closeSubscription()

                assertEquals(user.pubKey, login.result.pubKeyHex)
                assertEquals(NostrConnectURI.buildBunker(user.pubKey, setOf(relay), secret = null), login.bunkerUri)
            } finally {
                net.close()
            }
        }

    /** The address names one account and the signer signs for another: no login. */
    @Test
    fun aSignerForAnotherAccountIsRefused() =
        runBlocking(Dispatchers.Default) {
            val net = Net(relay)
            val someoneElse = NostrSignerInternal(KeyPair())
            val transport = NostrSignerInternal(KeyPair())
            net.runSigner(someoneElse, transport, setOf(relay))
            val addressKey = KeyPair().pubKey.toHexKey()
            try {
                val e =
                    assertFailsWith<Nip05SignerLoginUseCase.SignerMismatchException> {
                        withTimeout(20_000) {
                            Nip05SignerLoginUseCase.execute(Nip05RemoteSigner(addressKey, transport.pubKey, setOf(relay)), KeyPair(), net.client)
                        }
                    }
                assertEquals(someoneElse.pubKey, e.actual)
            } finally {
                net.close()
            }
        }

    /**
     * Spec shape with a `nostrconnect_url`: the signer's page carries our code, goes through the
     * approval queue like an auth_url, and the signer answers that code. The page comes down after.
     */
    @Test
    fun theSignersPageCarriesTheCodeAndTheLoginEndsOnTheAddress() =
        runBlocking(Dispatchers.Default) {
            val net = Net(relay)
            val user = NostrSignerInternal(KeyPair())
            val transport = NostrSignerInternal(KeyPair())
            net.runSigner(user, transport, setOf(relay))
            val prompts = RemoteSignerAuthPrompts()
            val opened = CopyOnWriteArrayList<String>()
            try {
                val login =
                    withTimeout(20_000) {
                        Nip05SignerLoginUseCase.execute(
                            signer = Nip05RemoteSigner(user.pubKey, transport.pubKey, setOf(relay), "https://signer.example/connect/<nostrconnect>"),
                            transport = KeyPair(),
                            client = net.client,
                            authPrompts = prompts,
                            onAuthUrl = { page ->
                                opened.add(page)
                                // The user approves on the signer's page: the signer answers the code.
                                val code = URLDecoder.decode(page.removePrefix("https://signer.example/connect/"), "UTF-8")
                                val offer = NostrConnectURI.parseNostrConnect(code)!!
                                net.scope.launch {
                                    delay(500)
                                    net.client.publish(NostrConnectEvent.create(BunkerResponse(newSubId(), offer.secret, null), offer.clientPubKey, transport), offer.relays)
                                }
                            },
                        )
                    }
                login.result.signer.closeSubscription()

                assertEquals(user.pubKey, login.result.pubKeyHex)
                assertEquals(1, opened.size)
                assertTrue(opened.single().startsWith("https://signer.example/connect/nostrconnect%3A%2F%2F"))
                assertEquals(emptyList(), prompts.pending.value, "the page comes down once the login is over")
            } finally {
                net.close()
            }
        }
}
