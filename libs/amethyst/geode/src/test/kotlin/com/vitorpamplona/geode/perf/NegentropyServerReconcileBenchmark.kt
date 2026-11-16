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
package com.vitorpamplona.geode.perf

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.interop.InteropSyncDriver
import com.vitorpamplona.geode.relayIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reproduces relayBench's NIP-77 initial-reconcile phase against a **real
 * in-process geode server** (KtorRelay → RelaySession → NegSessionRegistry
 * → live-index snapshot → NegentropyServerSession), driven by the real
 * client over a loopback WebSocket. Because client and server share one
 * JVM, a JFR recording of this run attributes cost across geode's full
 * request path — not just the reconciliation library.
 *
 * The companion [com.vitorpamplona.quartz.nip01Core.relay.prodbench.NegentropyReconcileBenchmark]
 * times the *library only* (no server, no wire): ~200 ms server compute at
 * the same 1M/200k-diff shape. The head-to-head relayBench run measured
 * geode's live server at ~6 s. This benchmark closes that gap in a
 * profilable single process, so we can see where the 30× goes.
 *
 * Opt-in (heavy — seeds up to 800k events): `-DnegServerBench=1`, size via
 * `-DnegBenchN=1000000`. FTS is off (irrelevant to reconcile) so seeding is
 * fast; the live negentropy index stays on, exactly as production runs it.
 */
class NegentropyServerReconcileBenchmark {
    companion object {
        val enabled = System.getProperty("negServerBench") == "1"
        val N = System.getProperty("negBenchN")?.toInt() ?: 200_000
        const val BASE_TIME = 1_704_067_200L
        const val EVENTS_PER_SECOND = 3
    }

    private fun mix(seed: Long): Long {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private val hexChars = "0123456789abcdef".toCharArray()

    private fun idFor(index: Int): String {
        val out = CharArray(64)
        for (w in 0 until 4) {
            val v = mix(index.toLong() * 4 + w)
            for (b in 0 until 8) {
                val byte = ((v ushr (b * 8)) and 0xFF).toInt()
                val pos = (w * 8 + b) * 2
                out[pos] = hexChars[byte ushr 4]
                out[pos + 1] = hexChars[byte and 0xF]
            }
        }
        return String(out)
    }

    private val pubkey = "00".repeat(32)
    private val sig = "0".repeat(128)

    private fun event(index: Int): Event =
        EventFactory.create(
            id = idFor(index),
            pubKey = pubkey,
            createdAt = BASE_TIME + (index / EVENTS_PER_SECOND).toLong(),
            kind = 1,
            tags = emptyArray(),
            content = "",
            sig = sig,
        )

    @Test
    fun serverReconcileAgainstRealGeode() =
        runBlocking {
            if (!enabled) {
                println("[skip] NegentropyServerReconcileBenchmark — set -DnegServerBench=1 to enable")
                return@runBlocking
            }

            // FTS off (not on the reconcile path); live index on (it is).
            val store = EventStore(dbName = null, indexStrategy = relayIndexingStrategy(fullTextSearch = false))
            val relay = RelayEngine(url = "ws://127.0.0.1:7000/".normalizeRelayUrl(), store = store)
            val server = KtorRelay(relay, host = "127.0.0.1", port = 0).start()
            val http = OkHttpClient.Builder().build()

            try {
                // Server holds [0.2N, N); client holds [0, 0.8N). Contiguous
                // diff blocks (oldest 20% + newest 20%) — relayBench's shape.
                val serverEvents = ((N * 2 / 10) until N).map { event(it) }
                val clientEvents = (0 until (N * 8 / 10)).map { event(it) }

                val seedStart = System.nanoTime()
                serverEvents.chunked(5000).forEach { store.batchInsert(it) }
                val seedMs = (System.nanoTime() - seedStart) / 1e6

                val expectedNeed = ((N * 8 / 10) until N).count() // server-only (newest 20%)
                val expectedHave = (0 until (N * 2 / 10)).count() // client-only (oldest 20%)

                val driver = InteropSyncDriver(http)
                val filter = Filter()

                val recStart = System.nanoTime()
                val res = driver.negotiate(server.url, filter, clientEvents, timeoutMs = 120_000, maxRounds = 256)
                val recMs = (System.nanoTime() - recStart) / 1e6

                println("─ NegentropyServerReconcileBenchmark @ ${N / 1000}k (real geode server) ─")
                println("  seed (${serverEvents.size} events): ${"%.0f".format(seedMs)} ms")
                println("  rounds:            ${res.rounds}")
                println("  negotiate wall:    ${"%.1f".format(recMs)} ms  ← geode server + client + wire")
                println("  need=${res.needIds.size} (exp $expectedNeed)  have=${res.haveIds.size} (exp $expectedHave)")
                println("  error: ${res.error}")

                assertEquals(null, res.error, "reconcile error")
                assertEquals(expectedNeed, res.needIds.size, "need set")
                assertEquals(expectedHave, res.haveIds.size, "have set")
            } finally {
                server.stop()
                relay.close()
                http.dispatcher.executorService.shutdown()
            }
        }
}
