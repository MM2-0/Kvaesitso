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

/**
 * Drives geode's REAL relay-to-relay mirror over the production transport
 * (upstream [KtorRelay] Ktor WebSocket ⇄ [MirrorWorker]'s OkHttp client) with a
 * large event volume, and asserts the downstream ends up with the whole set.
 *
 * This is the one path the in-process ingest guards can't cover: they call
 * `IngestQueue.submit` directly and are proven lossless (BatchInsertLossTest,
 * ConcurrentIngestLossTest). The geode↔geode relayBench sync lost ~0.05% of a
 * 40k delta; if that loss is in the WebSocket serve/receive (not the store),
 * streaming tens of thousands of events through a single REQ subscription
 * surfaces it as a downstream count short of the upstream.
 */
class MirrorSyncLossTest {
    private val upstreamStore = EventStore(null)
    private val downstreamStore = EventStore(null)

    private val upstream =
        RelayEngine(url = "ws://127.0.0.1:7896/".normalizeRelayUrl(), store = upstreamStore)
    private val downstream =
        RelayEngine(url = "ws://127.0.0.1:7897/".normalizeRelayUrl(), store = downstreamStore, parallelVerify = true)

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

    @Test
    fun mirrorSyncDeliversEveryEvent() =
        runBlocking {
            val count = System.getProperty("mirrorLossN")?.toInt() ?: 50_000
            val now = TimeUtils.now()
            // Recent-past window so the mirror's `since = now - backfill` covers
            // every event. Empty tags + kind 1 mirror the events relayBench lost.
            val sig = "f".repeat(128)
            val events =
                (0 until count).map { i ->
                    Event(
                        id = hex64(7, i),
                        pubKey = hex64(3, i % 5000),
                        createdAt = now - 60 - (i % 3000),
                        kind = 1,
                        tags = emptyArray(),
                        content = "e$i",
                        sig = sig,
                    )
                }
            // Bulk-load the upstream directly (bypass wire; no verify).
            events.chunked(10_000).forEach { upstreamStore.batchInsert(it) }
            assertEquals(count, upstreamStore.count(Filter()), "upstream preloaded")

            server = KtorRelay(upstream, host = "127.0.0.1", port = 7896).start()

            worker =
                MirrorWorker(
                    upstreams =
                        listOf(
                            MirrorUpstream(
                                url = "ws://127.0.0.1:7896/".normalizeRelayUrl(),
                                trusted = true,
                                backfillSeconds = 86_400,
                            ),
                        ),
                    server = downstream.server,
                ).also { it.start() }

            // Poll until the downstream stops growing; a lossless sync reaches
            // `count`, a lossy one plateaus below it.
            var last = -1
            var stable = 0
            var reached = 0
            withTimeoutOrNull(180_000) {
                while (true) {
                    val c = downstreamStore.count(Filter())
                    reached = c
                    if (c >= count) break
                    if (c == last) {
                        if (++stable >= 20) break // ~10s with no progress
                    } else {
                        stable = 0
                        last = c
                    }
                    delay(500)
                }
            }

            println("─ MirrorSyncLossTest: upstream=$count downstream=$reached (missing ${count - reached}) ─")
            assertEquals(count, reached, "mirror sync dropped ${count - reached} of $count events over the wire")
        }
}
