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

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
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
 * The mirror is a long-running daemon, so surviving its upstream is not
 * optional. This drives the REAL production transport (OkHttp WebSocket
 * against a real Ktor port — no in-process shortcut): the upstream is
 * stopped mid-mirror and a new instance is brought up on the same port.
 * The worker must ride through the disconnect (NostrClient's backoff +
 * keep-alive own the re-dial), re-send its REQ, drop the replayed
 * duplicate, and pull the event that only exists on the new instance.
 *
 * Timing note: after a stable connection drops, the client's backoff is
 * reset to 1s and re-dial attempts double from there. The worker's 5s
 * retry pump nudges the pool well ahead of NostrClient's own 60s
 * keep-alive (without it, this test measured a 61s blackout when the
 * immediate re-dial raced the port rebind; with it, ~6s). The generous
 * timeout only covers a worst-case scheduling stall on a loaded CI
 * runner.
 */
class MirrorWorkerReconnectTest {
    private val downstreamStore = EventStore(null)
    private val downstream =
        RelayEngine(
            url = "ws://127.0.0.1:7797/".normalizeRelayUrl(),
            store = downstreamStore,
            parallelVerify = true,
        )

    private var worker: MirrorWorker? = null
    private var upstreamServer: KtorRelay? = null
    private var upstreamEngine: RelayEngine? = null

    @After
    fun tearDown() {
        worker?.close()
        upstreamServer?.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        upstreamEngine?.close()
        downstream.close()
    }

    private fun startUpstream(port: Int): KtorRelay {
        val engine = RelayEngine(url = "ws://127.0.0.1:7796/".normalizeRelayUrl())
        val server = KtorRelay(engine, host = "127.0.0.1", port = port).start()
        upstreamEngine = engine
        upstreamServer = server
        return server
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

    private suspend fun awaitDownstreamCount(expected: Int) =
        withTimeout(120_000) {
            while (downstreamStore.count(Filter()) < expected) delay(50)
        }

    @Test
    fun mirrorSurvivesUpstreamRestart() =
        runBlocking {
            val beforeRestart = forgedEvent(1)
            val afterRestart = forgedEvent(2)

            // First upstream instance on an OS-assigned port.
            val first = startUpstream(port = 0)
            val port =
                first.url
                    .normalizeRelayUrl()
                    .url
                    .substringAfterLast(':')
                    .trimEnd('/')
                    .toInt()
            upstreamEngine!!.preload(beforeRestart)

            // Real OkHttp transport: websocketBuilder deliberately omitted.
            val mirror =
                MirrorWorker(
                    upstreams =
                        listOf(
                            MirrorUpstream(
                                url = first.url.normalizeRelayUrl(),
                                trusted = true,
                                backfillSeconds = 3600,
                            ),
                        ),
                    server = downstream.server,
                ).also { worker = it }
            mirror.start()
            awaitDownstreamCount(1)

            // Kill the upstream: every socket drops, the port closes.
            first.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
            upstreamEngine!!.close()

            // Bring up a NEW instance on the SAME port. Its store has the
            // old event (so the re-sent REQ replays a duplicate) plus one
            // that only exists post-restart.
            val second = startUpstream(port = port)
            upstreamEngine!!.preload(beforeRestart, afterRestart)

            // The worker must reconnect on its own — no external poke —
            // re-subscribe, and pull the new event.
            awaitDownstreamCount(2)

            assertEquals(
                setOf(beforeRestart.id, afterRestart.id),
                downstreamStore.query<Event>(Filter()).map { it.id }.toSet(),
            )
            // The duplicate replay of the pre-restart event was dropped by
            // the store, not double-inserted.
            assertEquals(2, downstreamStore.count(Filter()))

            // The disconnect advanced the REQ's since watermark: the
            // reconnect subscribed from ~(newest event - overlap), not
            // from the boot-time `now - backfill`. Without this the
            // reconnect would replay the whole backfill window every flap.
            assertTrue(mirror.sinceAdvances.get() >= 1)

            second.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        }
}
