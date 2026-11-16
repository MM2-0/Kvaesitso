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

import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySession
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end NIP-77 reconciliation through `InProcessRelays` + the
 * in-process WebSocket bridge.
 *
 *  - The relay is preloaded with a known set of events.
 *  - A [NegentropySession] is initialised with a partially-overlapping
 *    set on the client side.
 *  - We drive the NEG-OPEN / NEG-MSG round trips manually until
 *    `processMessage` reports completion.
 *  - We assert that `haveIds` (events the client has that the relay
 *    doesn't) and `needIds` (events the relay has that the client
 *    doesn't) cover exactly the symmetric difference.
 *
 * NEG-CLOSE is exercised separately — sending NEG-MSG after CLOSE
 * must surface a NEG-ERR from the relay.
 */
class Nip77NegentropyTest {
    private lateinit var hub: InProcessRelays
    private val relayUrl: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://127.0.0.1:7770/")

    @BeforeTest
    fun setup() {
        hub = InProcessRelays()
    }

    @AfterTest
    fun teardown() {
        hub.close()
    }

    /**
     * Bare-bones WS client that captures every server message into a
     * channel and exposes a `send` that goes straight at the in-process
     * bridge. We don't need NostrClient's filter-management for these
     * tests — we drive the wire.
     */
    private class WireClient(
        hub: InProcessRelays,
        url: NormalizedRelayUrl,
    ) {
        val incoming: Channel<String> = Channel(UNLIMITED)
        private val ws =
            hub.build(
                url,
                object : WebSocketListener {
                    override fun onOpen(
                        pingMillis: Int,
                        compression: Boolean,
                    ) {}

                    override suspend fun onMessage(text: String) {
                        incoming.trySend(text)
                    }

                    override fun onClosed(
                        code: Int,
                        reason: String,
                    ) {
                        incoming.close()
                    }

                    override fun onFailure(
                        t: Throwable,
                        code: Int?,
                        response: String?,
                    ) {
                        incoming.close(t)
                    }
                },
            )

        init {
            ws.connect()
        }

        fun send(json: String) {
            check(ws.send(json)) { "send returned false" }
        }

        fun close() {
            ws.disconnect()
        }
    }

    private suspend fun WireClient.nextMessage(timeoutMs: Long = 5_000): Message {
        val raw = withTimeout(timeoutMs) { incoming.receive() }
        return OptimizedJsonMapper.fromJsonToMessage(raw)
    }

    /** Generates [count] signed text notes with monotonic createdAt. */
    private fun makeEvents(count: Int): List<Event> {
        val signer = NostrSignerSync(KeyPair())
        val now = 1_700_000_000L
        return List(count) { i ->
            signer.sign(TextNoteEvent.build("event-$i", createdAt = now + i))
        }
    }

    @Test
    fun negentropyComputesSymmetricDifference() =
        runBlocking {
            // Universe of 10 events. Relay has events [0..7], client has [3..9] —
            // overlap [3..7], relay-only [0..2], client-only [8..9].
            val all = makeEvents(10)
            val relayEvents = all.subList(0, 8)
            val clientEvents = all.subList(3, 10)

            hub.getOrCreate(relayUrl).preload(relayEvents)

            val client = WireClient(hub, relayUrl)
            try {
                val session =
                    NegentropySession.fromEvents(
                        subId = "neg-1",
                        filter = Filter(kinds = listOf(1)),
                        localEvents = clientEvents,
                    )

                // Step 1: send NEG-OPEN.
                val openCmd = session.open()
                client.send(OptimizedJsonMapper.toJson(openCmd))

                // Step 2: drive NEG-MSG round trips until reconciliation completes.
                val haveIds = mutableSetOf<String>()
                val needIds = mutableSetOf<String>()
                var safety = 32
                while (safety-- > 0) {
                    val response = client.nextMessage()
                    if (response is NegErrMessage) {
                        kotlin.test.fail("relay sent NEG-ERR: ${response.reason}")
                    }
                    response as NegMsgMessage
                    val result = session.processMessage(response.message)
                    haveIds += result.haveIds
                    needIds += result.needIds
                    if (result.isComplete()) break
                    client.send(OptimizedJsonMapper.toJson(result.nextCmd!!))
                }
                assertTrue(safety > 0, "reconciliation did not converge in 32 rounds")

                // Step 3: verify the symmetric difference.
                val expectedNeed = relayEvents.subList(0, 3).map { it.id }.toSet() // [0..2]
                val expectedHave = clientEvents.subList(5, 7).map { it.id }.toSet() // [8..9]
                assertEquals(expectedNeed, needIds, "client should NEED events 0..2 from relay")
                assertEquals(expectedHave, haveIds, "client should HAVE events 8..9 to send to relay")
            } finally {
                client.close()
            }
        }

    @Test
    fun negCloseFreesServerStateAndReopenWorks() =
        runBlocking {
            val all = makeEvents(5)
            hub.getOrCreate(relayUrl).preload(all)

            val client = WireClient(hub, relayUrl)
            try {
                // First session.
                val s1 = NegentropySession.fromEvents("neg", Filter(kinds = listOf(1)), localEvents = emptyList())
                client.send(OptimizedJsonMapper.toJson(s1.open()))
                val response = client.nextMessage() as NegMsgMessage
                val r1 = s1.processMessage(response.message)
                // Client had nothing, so it needs all 5 from the relay.
                assertEquals(5, r1.needIds.size)

                // Close.
                client.send(OptimizedJsonMapper.toJson(s1.close()))

                // Re-OPEN with the same subId and an empty client store —
                // server must build a new session and respond. If the
                // close didn't free state, this would either error or
                // continue the previous reconciliation.
                val s2 = NegentropySession.fromEvents("neg", Filter(kinds = listOf(1)), localEvents = emptyList())
                client.send(OptimizedJsonMapper.toJson(s2.open()))
                val resp2 = client.nextMessage() as NegMsgMessage
                val r2 = s2.processMessage(resp2.message)
                assertEquals(5, r2.needIds.size)
            } finally {
                client.close()
            }
        }

    @Test
    fun negMsgWithoutOpenReturnsNegErr() =
        runBlocking {
            val client = WireClient(hub, relayUrl)
            try {
                // Synthesise a stray NEG-MSG for a sub-id that was never opened.
                val raw = """["NEG-MSG","ghost-sub","00"]"""
                client.send(raw)
                val response = client.nextMessage()
                assertTrue(response is NegErrMessage, "expected NEG-ERR, got ${response::class.simpleName}")
                assertEquals("ghost-sub", response.subId)
                // strfry-parity wording — clients in the wild string-match this.
                assertEquals("closed: unknown subscription handle", response.reason)
            } finally {
                client.close()
            }
        }

    @Test
    fun negOpenSnapshotOverflowReturnsStrFryNegErr() =
        runBlocking {
            // Tiny cap so the test is fast. Preload more events than the
            // cap so NEG-OPEN must reject — strfry's parity behaviour for
            // `relay__negentropy__maxSyncEvents`.
            val capped = InProcessRelays(negentropySettings = NegentropySettings(maxSyncEvents = 5))
            try {
                val capUrl = RelayUrlNormalizer.normalize("ws://127.0.0.1:7771/")
                val events = makeEvents(20)
                capped.getOrCreate(capUrl).preload(events)

                val client = WireClient(capped, capUrl)
                try {
                    val session =
                        NegentropySession.fromEvents(
                            subId = "neg-overflow",
                            filter = Filter(kinds = listOf(1)),
                            localEvents = emptyList(),
                        )
                    client.send(OptimizedJsonMapper.toJson(session.open()))

                    val response = client.nextMessage()
                    assertTrue(response is NegErrMessage, "expected NEG-ERR, got ${response::class.simpleName}")
                    assertEquals("neg-overflow", response.subId)
                    // strfry-parity wording.
                    assertEquals("blocked: too many query results", response.reason)
                } finally {
                    client.close()
                }
            } finally {
                capped.close()
            }
        }

    @Test
    fun negOpenPerConnectionCapEmitsNotice() =
        runBlocking {
            // Cap = 2, so the third NEG-OPEN on one connection should
            // be rejected with a NOTICE (matching strfry's wording).
            val capped = InProcessRelays(negentropySettings = NegentropySettings(maxSessionsPerConnection = 2))
            try {
                val capUrl = RelayUrlNormalizer.normalize("ws://127.0.0.1:7772/")
                capped.getOrCreate(capUrl).preload(makeEvents(3))

                val client = WireClient(capped, capUrl)
                try {
                    repeat(2) { i ->
                        val s = NegentropySession.fromEvents("ok-$i", Filter(kinds = listOf(1)), localEvents = emptyList())
                        client.send(OptimizedJsonMapper.toJson(s.open()))
                        // Drain the NEG-MSG response so the next OPEN goes
                        // through cleanly.
                        client.nextMessage() as NegMsgMessage
                    }

                    // Third OPEN — should be rejected with a NOTICE.
                    val third = NegentropySession.fromEvents("third", Filter(kinds = listOf(1)), localEvents = emptyList())
                    client.send(OptimizedJsonMapper.toJson(third.open()))
                    val response = client.nextMessage()
                    assertTrue(response is NoticeMessage, "expected NOTICE, got ${response::class.simpleName}")
                    assertEquals("too many concurrent NEG requests", response.message)
                } finally {
                    client.close()
                }
            } finally {
                capped.close()
            }
        }

    @Test
    fun negOpenWithSameSubIdReplacesPriorSession() =
        runBlocking {
            val a = makeEvents(3)
            val b = makeEvents(2)
            hub.getOrCreate(relayUrl).preload(a + b)

            val client = WireClient(hub, relayUrl)
            try {
                // First open with localEvents = a; next we'll re-open
                // and confirm the new session sees a fresh state.
                val first = NegentropySession.fromEvents("dup", Filter(kinds = listOf(1)), localEvents = a)
                client.send(OptimizedJsonMapper.toJson(first.open()))
                client.nextMessage() as NegMsgMessage // discard

                // Re-OPEN with same subId, different localEvents.
                val second = NegentropySession.fromEvents("dup", Filter(kinds = listOf(1)), localEvents = a + b)
                client.send(OptimizedJsonMapper.toJson(second.open()))
                val resp = client.nextMessage() as NegMsgMessage
                val r = second.processMessage(resp.message)
                // Client now has every event the relay has → nothing to need.
                assertEquals(0, r.needIds.size)
                assertEquals(0, r.haveIds.size)
            } finally {
                client.close()
            }
        }
}
