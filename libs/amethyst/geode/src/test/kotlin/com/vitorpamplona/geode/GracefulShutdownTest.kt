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
package com.vitorpamplona.geode

import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests [KtorRelay.stop] honours the graceful-shutdown contract:
 *  1. Active clients receive a `NOTICE` warning of imminent shutdown.
 *  2. The active session counter accurately tracks open WS sessions.
 *  3. After `stop()` returns, no sessions remain registered.
 */
class GracefulShutdownTest {
    private lateinit var relay: RelayEngine
    private lateinit var server: KtorRelay
    private lateinit var scope: CoroutineScope
    private lateinit var client: NostrClient

    private val httpClient = OkHttpClient.Builder().build()

    @BeforeTest
    fun setup() {
        val placeholder = "ws://127.0.0.1:7771/".normalizeRelayUrl()
        relay = RelayEngine(url = placeholder)
        server = KtorRelay(relay, host = "127.0.0.1", port = 0).start()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val builder = BasicOkHttpWebSocket.Builder { _ -> httpClient }
        client = NostrClient(builder, scope)
    }

    @AfterTest
    fun teardown() {
        client.disconnect()
        scope.cancel()
        // server may already be stopped by the test; calling stop()
        // again is a no-op.
        server.stop(gracePeriodMillis = 200, timeoutMillis = 500)
        relay.close()
    }

    @Test
    fun activeSessionCountTracksConnectAndDisconnect() =
        runBlocking {
            assertEquals(0, server.activeSessionCount, "no clients yet")

            // Open a connection by subscribing — wait for EOSE so we
            // know the WebSocket handshake completed and the relay
            // session has registered.
            val gotEose = Channel<Unit>(UNLIMITED)
            val relayUrl = server.url.normalizeRelayUrl()
            client.subscribe(
                "track-1",
                mapOf(relayUrl to listOf(Filter(kinds = listOf(1)))),
                object : SubscriptionListener {
                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        gotEose.trySend(Unit)
                    }
                },
            )
            withTimeout(5000) { gotEose.receive() }

            assertEquals(1, server.activeSessionCount, "one connected session")

            client.unsubscribe("track-1")
            client.disconnect()

            // Disconnect happens asynchronously on the relay side; allow
            // a short window for the handler's `finally` block to run.
            withTimeoutOrNull(2000) {
                while (server.activeSessionCount > 0) kotlinx.coroutines.delay(10)
            }
            assertEquals(0, server.activeSessionCount, "session must be removed after disconnect")
        }

    @Test
    fun stopSendsShutdownNoticeToActiveClients() =
        runBlocking {
            val noticeChannel = Channel<NoticeMessage>(UNLIMITED)
            val gotEose = Channel<Unit>(UNLIMITED)
            val listener =
                object : RelayConnectionListener {
                    override suspend fun onIncomingMessage(
                        relay: IRelayClient,
                        msgStr: String,
                        msg: Message,
                    ) {
                        if (msg is NoticeMessage) noticeChannel.trySend(msg)
                    }
                }
            client.addConnectionListener(listener)

            val relayUrl = server.url.normalizeRelayUrl()
            client.subscribe(
                "notice-watch",
                mapOf(relayUrl to listOf(Filter(kinds = listOf(1)))),
                object : SubscriptionListener {
                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        gotEose.trySend(Unit)
                    }
                },
            )
            withTimeout(5000) { gotEose.receive() }
            assertEquals(1, server.activeSessionCount)

            // Trigger graceful shutdown.
            server.stop(gracePeriodMillis = 1_000, timeoutMillis = 2_000)

            val notice = withTimeout(5000) { noticeChannel.receive() }
            assertNotNull(notice)
            assertTrue(
                notice.message.startsWith("closing:"),
                "expected NOTICE to start with 'closing:', got '${notice.message}'",
            )
        }

    @Test
    fun stopIsIdempotent() {
        // First call shuts the engine down.
        server.stop(gracePeriodMillis = 100, timeoutMillis = 500)
        // Second call must be a safe no-op (no exception).
        server.stop(gracePeriodMillis = 100, timeoutMillis = 500)
    }

    /**
     * Sanity check on the grace window: a bare-bones ws client that
     * connects and never sends anything should *receive* the shutdown
     * NOTICE before the server fully closes the socket. Uses Ktor's
     * client-agnostic OkHttp transport directly so we can observe the
     * raw frames.
     */
    @Test
    fun rawWsClientObservesNoticeBeforeServerCloses() =
        runBlocking {
            val httpUrl =
                server.url
                    .replace("ws://", "http://")
            val request =
                okhttp3.Request
                    .Builder()
                    .url(httpUrl)
                    .build()

            val frames = Channel<String>(UNLIMITED)
            val socket =
                httpClient.newWebSocket(
                    request,
                    object : okhttp3.WebSocketListener() {
                        override fun onMessage(
                            webSocket: okhttp3.WebSocket,
                            text: String,
                        ) {
                            frames.trySend(text)
                        }
                    },
                )

            try {
                // Wait until the relay sees the connection.
                withTimeoutOrNull(2000) {
                    while (server.activeSessionCount == 0) kotlinx.coroutines.delay(10)
                }
                assertEquals(1, server.activeSessionCount)

                server.stop(gracePeriodMillis = 1_000, timeoutMillis = 2_000)

                val text = withTimeout(3000) { frames.receive() }
                assertTrue(
                    text.contains("\"NOTICE\"") && text.contains("closing"),
                    "expected a NOTICE frame, got: $text",
                )
            } finally {
                socket.cancel()
            }
        }
}
