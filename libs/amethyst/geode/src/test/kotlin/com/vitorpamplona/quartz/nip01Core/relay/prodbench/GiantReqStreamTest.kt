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
package com.vitorpamplona.quartz.nip01Core.relay.prodbench

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test

/**
 * Regression guard for two server-side bugs that made a single giant REQ
 * (bulk download) crawl and then wedge:
 *
 *  1. LiveEventStore's replay dedup used an immutable Set with copy-on-add —
 *     accidentally O(n²): a 100k-event REQ streamed at ~700 events/s, and
 *     20k at ~2.4k events/s.
 *  2. Fixing (1) unmasked the session pump's slow-client policy: a fast
 *     replay overflowed the 8192-frame backlog cap instantly, and the "drop"
 *     only closed the internal queue — the client hung forever with no EOSE
 *     and no close frame (and silently missed the tail of the response).
 *
 * Post-fix this streams 20k events in well under a second (~27k events/s
 * measured); the assertion bound is generous for CI noise.
 */
class GiantReqStreamTest {
    @Test
    fun giantReqStreamsFastAndComplete() {
        val engine = RelayEngine(url = "ws://127.0.0.1:7771/".normalizeRelayUrl())
        runBlocking {
            val events = (1..20_000).map { SyntheticEvents.fakeEvent(idSeed = it, kind = 1, createdAt = it.toLong(), content = "x".repeat(200)) }
            events.chunked(2000).forEach { engine.store.batchInsert(it) }
        }
        val server = KtorRelay(engine, port = 0).start()
        val http = OkHttpClient.Builder().build()
        try {
            val count = AtomicLong(0)
            val done = CountDownLatch(1)
            val start = System.nanoTime()
            val ws =
                http.newWebSocket(
                    Request.Builder().url(server.url).build(),
                    object : okhttp3.WebSocketListener() {
                        override fun onOpen(
                            webSocket: okhttp3.WebSocket,
                            response: Response,
                        ) {
                            webSocket.send("""["REQ","big",{"kinds":[1]}]""")
                        }

                        override fun onMessage(
                            webSocket: okhttp3.WebSocket,
                            text: String,
                        ) {
                            if (text.startsWith("[\"EVENT\"")) {
                                count.incrementAndGet()
                            } else if (text.startsWith("[\"EOSE\"")) {
                                done.countDown()
                            }
                        }

                        override fun onFailure(
                            webSocket: okhttp3.WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            done.countDown()
                        }
                    },
                )
            done.await(300, TimeUnit.SECONDS)
            val wall = System.nanoTime() - start
            ws.cancel()
            println("SCRATCH giant REQ: got=${count.get()}/20000 in %.1fs -> %.0f events/s".format(wall / 1e9, count.get() * 1e9 / wall))
        } finally {
            http.dispatcher.executorService.shutdown()
            server.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
            engine.close()
        }
    }
}
