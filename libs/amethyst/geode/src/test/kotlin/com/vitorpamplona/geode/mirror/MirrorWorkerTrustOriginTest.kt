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
package com.vitorpamplona.geode.mirror

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.testing.publish
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `trusted = true` skip-verify decision must be bound to the relay
 * that DELIVERED the event, not to the subscription id it arrived on.
 * The client pool dispatches EVENT messages by subscription id alone and
 * every `[[mirror]]` upstream shares one client — so a hostile untrusted
 * upstream that answers with the *trusted* upstream's subscription id
 * would otherwise ride its skip-verify straight into the store.
 */
class MirrorWorkerTrustOriginTest {
    private val trustedUrl = RelayUrlNormalizer.normalize("ws://trusted.relay/")
    private val hostileUrl = RelayUrlNormalizer.normalize("ws://hostile.relay/")
    private val downstreamUrl = RelayUrlNormalizer.normalize("ws://downstream.relay/")

    private val hub = InProcessRelays()

    private val downstreamStore = EventStore(null)
    private val downstream =
        RelayEngine(
            url = downstreamUrl,
            store = downstreamStore,
            parallelVerify = true,
        )

    private var worker: MirrorWorker? = null

    @After
    fun tearDown() {
        worker?.close()
        downstream.close()
        hub.close()
    }

    private fun forgedEvent(idSeed: Int): Event =
        Event(
            id = idSeed.toString().padStart(64, '0'),
            pubKey = "1".repeat(64),
            createdAt = TimeUtils.now() - idSeed,
            kind = 1,
            tags = emptyArray(),
            content = "forged $idSeed",
            sig = "f".repeat(128),
        )

    /**
     * A relay that never answers the REQ it was given; instead, on
     * connect it injects one EVENT frame carrying a subscription id that
     * belongs to a DIFFERENT upstream's subscription.
     */
    private class HijackingWebSocket(
        private val out: WebSocketListener,
        private val frame: String,
    ) : WebSocket {
        private var connected = false

        override fun needsReconnect(): Boolean = !connected

        override fun connect() {
            connected = true
            out.onOpen(0, false)
            kotlinx.coroutines.runBlocking { out.onMessage(frame) }
        }

        override fun disconnect() {
            connected = false
        }

        override fun send(msg: String): Boolean = true
    }

    @Test
    fun hostileUpstreamCannotRideAnotherSubscriptionsTrust() =
        runBlocking {
            val forged = forgedEvent(1)
            // The trusted upstream is configured FIRST, so its down
            // subscription id is "geode-mirror-0" — which the hostile
            // relay claims in its injected frame.
            val hijackFrame = """["EVENT","geode-mirror-0",${forged.toJson()}]"""

            val builder =
                object : WebsocketBuilder {
                    override fun build(
                        url: NormalizedRelayUrl,
                        out: WebSocketListener,
                    ): WebSocket =
                        if (url == hostileUrl) {
                            HijackingWebSocket(out, hijackFrame)
                        } else {
                            hub.build(url, out)
                        }
                }

            val mirror =
                MirrorWorker(
                    upstreams =
                        listOf(
                            MirrorUpstream(trustedUrl, trusted = true, backfillSeconds = 3600),
                            MirrorUpstream(hostileUrl, trusted = false, backfillSeconds = 3600),
                        ),
                    server = downstream.server,
                    websocketBuilder = builder,
                ).also { worker = it }
            mirror.start()

            // The injected frame must be dropped at the origin check —
            // observable as a `filtered` tick, never as a stored row.
            withTimeout(15_000) {
                while (mirror.filtered.get() < 1) delay(25)
            }
            assertTrue(downstreamStore.query<Event>(Filter(ids = listOf(forged.id))).isEmpty())

            // The genuinely trusted upstream still works on the same
            // subscription id the hostile relay tried to claim.
            val real = forgedEvent(2)
            hub.getOrCreate(trustedUrl).publish(real)
            withTimeout(15_000) {
                while (downstreamStore.count(Filter()) < 1) delay(25)
            }
            assertEquals(
                listOf(real.id),
                downstreamStore.query<Event>(Filter()).map { it.id },
            )
        }
}
