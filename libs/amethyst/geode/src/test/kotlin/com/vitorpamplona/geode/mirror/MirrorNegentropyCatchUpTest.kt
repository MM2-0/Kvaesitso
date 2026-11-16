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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves geode's two-phase mirror — the strfry model — over the real transport:
 * a NIP-77 negentropy "sync" catch-up for the historical window, then the live
 * REQ tail.
 *
 * The catch-up is isolated from the live tail by construction: the upstream is
 * preloaded with **historical** events (created_at in the past) and the live
 * subscription starts at `now`, so the live sub can never deliver them — only
 * the catch-up covers `[now - backfill, now]`. Reaching the full count therefore
 * proves the negentropy sync ran and completed. A fresh event published *after*
 * boot then proves the live tail is still running alongside it.
 */
class MirrorNegentropyCatchUpTest {
    private val upstreamStore = EventStore(null)
    private val downstreamStore = EventStore(null)

    private val upstream =
        RelayEngine(url = "ws://127.0.0.1:7894/".normalizeRelayUrl(), store = upstreamStore)
    private val downstream =
        RelayEngine(url = "ws://127.0.0.1:7895/".normalizeRelayUrl(), store = downstreamStore, parallelVerify = true)

    private var server: KtorRelay? = null
    private var worker: MirrorWorker? = null

    @AfterTest
    fun tearDown() {
        worker?.close()
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        upstream.close()
        downstream.close()
    }

    private val hex = "0123456789abcdef"

    private fun mix(seed: Long): Long {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private fun hex64(
        salt: Long,
        index: Int,
    ): String {
        val out = CharArray(64)
        for (w in 0 until 4) {
            val v = mix(salt * 1_000_003 + index.toLong() * 4 + w)
            for (b in 0 until 8) {
                val byte = ((v ushr (b * 8)) and 0xFF).toInt()
                out[(w * 8 + b) * 2] = hex[byte ushr 4]
                out[(w * 8 + b) * 2 + 1] = hex[byte and 0xF]
            }
        }
        return String(out)
    }

    private suspend fun awaitCount(
        store: EventStore,
        target: Int,
        timeoutMs: Long = 120_000,
    ): Int {
        var reached = 0
        var last = -1
        var stable = 0
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val c = store.count(Filter())
                reached = c
                if (c >= target) break
                if (c == last) {
                    if (++stable >= 30) break // ~15s with no progress
                } else {
                    stable = 0
                    last = c
                }
                delay(500)
            }
        }
        return reached
    }

    @Test
    fun negentropyCatchUpThenLiveTail() =
        runBlocking {
            val count = System.getProperty("catchUpN")?.toInt() ?: 3_000
            val now = TimeUtils.now()
            val sig = "f".repeat(128)

            // Historical events: all safely in the past (but inside the backfill
            // window), so the live-only sub (since = boot now) can NOT deliver
            // them. Only the negentropy catch-up covers this range.
            val history =
                (0 until count).map { i ->
                    Event(
                        id = hex64(7, i),
                        pubKey = hex64(3, i % 500),
                        createdAt = now - 3_600 - (i % 1_000),
                        kind = 1,
                        tags = emptyArray(),
                        content = "h$i",
                        sig = sig,
                    )
                }
            history.chunked(10_000).forEach { upstreamStore.batchInsert(it) }
            assertEquals(count, upstreamStore.count(Filter()), "upstream preloaded")

            server = KtorRelay(upstream, host = "127.0.0.1", port = 7894).start()

            worker =
                MirrorWorker(
                    upstreams =
                        listOf(
                            MirrorUpstream(
                                url = "ws://127.0.0.1:7894/".normalizeRelayUrl(),
                                trusted = true,
                                backfillSeconds = 86_400,
                            ),
                        ),
                    server = downstream.server,
                    store = downstreamStore,
                    negentropyBackfill = true,
                ).also { it.start() }

            // Phase 1: the catch-up must deliver every historical event.
            val afterCatchUp = awaitCount(downstreamStore, count)
            assertEquals(count, afterCatchUp, "negentropy catch-up dropped ${count - afterCatchUp} of $count historical events")

            // Phase 2: a fresh event published AFTER boot proves the live REQ
            // tail is running alongside the catch-up. Published through the
            // upstream server so it fans out to the live subscription.
            val live =
                Event(
                    id = hex64(9, 1),
                    pubKey = hex64(3, 0),
                    createdAt = TimeUtils.now(),
                    kind = 1,
                    tags = emptyArray(),
                    content = "live",
                    sig = sig,
                )
            upstream.server.ingest(live, skipVerify = true) { }

            val afterLive = awaitCount(downstreamStore, count + 1)
            assertEquals(count + 1, afterLive, "live tail did not deliver the post-boot event")
            assertTrue(
                downstreamStore.count(Filter(ids = listOf(live.id))) == 1,
                "the live event is present downstream",
            )

            println("─ MirrorNegentropyCatchUpTest(down): catch-up $count + live 1 = $afterLive delivered ─")
        }

    /**
     * The mirror image — `dir = up`, geode's `strfry sync --dir up`. The local
     * geode holds a set of historical events; the upstream sink is empty. The
     * up catch-up must reconcile and PUSH every local event to the sink. Uses a
     * default (no-verify) sink so the synthetic-signature events are accepted.
     */
    @Test
    fun negentropyCatchUpPushesUp() =
        runBlocking {
            val count = System.getProperty("catchUpUpN")?.toInt() ?: 3_000
            val now = TimeUtils.now()
            val sig = "f".repeat(128)

            // The LOCAL geode (downstream) holds the events; the sink (upstream)
            // starts empty and must receive them all via the up push.
            val history =
                (0 until count).map { i ->
                    Event(
                        id = hex64(11, i),
                        pubKey = hex64(3, i % 500),
                        createdAt = now - 3_600 - (i % 1_000),
                        kind = 1,
                        tags = emptyArray(),
                        content = "u$i",
                        sig = sig,
                    )
                }
            history.chunked(10_000).forEach { downstreamStore.batchInsert(it) }
            assertEquals(count, downstreamStore.count(Filter()), "local geode preloaded")
            assertEquals(0, upstreamStore.count(Filter()), "sink starts empty")

            // The sink is the relay geode dials; it must accept what we push.
            server = KtorRelay(upstream, host = "127.0.0.1", port = 7894).start()

            worker =
                MirrorWorker(
                    upstreams =
                        listOf(
                            MirrorUpstream(
                                url = "ws://127.0.0.1:7894/".normalizeRelayUrl(),
                                trusted = false,
                                backfillSeconds = 86_400,
                                direction = MirrorDirection.UP,
                            ),
                        ),
                    server = downstream.server,
                    store = downstreamStore,
                    negentropyBackfill = true,
                ).also { it.start() }

            val pushed = awaitCount(upstreamStore, count)
            assertEquals(count, pushed, "up catch-up failed to push ${count - pushed} of $count events")
            println("─ MirrorNegentropyCatchUpTest(up): pushed $pushed/$count to the sink via negentropy ─")
        }
}
