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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Does OkHttp's `Dispatcher.maxRequests` cap *concurrent handshakes*, or does a live
 * WebSocket hold its dispatcher slot for the socket's whole lifetime?
 *
 * This decides whether lowering `maxRequests` on the relay `OkHttpClient`
 * (`OkHttpClientFactoryForRelays`, currently 1024) is a safe way to throttle the
 * cold-start dial burst — or a change that would silently cap the app at N relays
 * forever. The app connects to ~190 relays at once and spikes to ~640 threads, so
 * this knob looks tempting; the difference between the two behaviours is the
 * difference between a one-line fix and an outage.
 *
 * The answer is not something to take from memory: `RealWebSocket.connect()` goes
 * through `newCall().enqueue()`, so the upgrade *is* dispatched, and whether the
 * call is retired when the 101 response arrives is an implementation detail that
 * has changed across OkHttp versions.
 */
class OkHttpDispatcherWebSocketCapTest {
    private lateinit var relay: RelayEngine
    private lateinit var server: KtorRelay

    companion object {
        const val CAP = 4
        const val SOCKETS = 20
    }

    @BeforeTest
    fun setup() {
        relay = RelayEngine(url = "ws://127.0.0.1:7771/".normalizeRelayUrl())
        server = KtorRelay(relay, host = "127.0.0.1", port = 0).start()
    }

    @AfterTest
    fun teardown() {
        server.stop()
        relay.close()
    }

    @Test
    fun liveWebSocketsHoldTheirDispatcherSlotForever() {
        val client =
            OkHttpClient
                .Builder()
                .dispatcher(
                    Dispatcher().apply {
                        maxRequests = CAP
                        // all sockets share one host here, so this would bind too
                        maxRequestsPerHost = CAP
                    },
                ).build()

        val opened = AtomicInteger()
        val failed = AtomicInteger()
        val latch = CountDownLatch(SOCKETS)
        val sockets = mutableListOf<WebSocket>()

        val url = server.url.replace("ws://", "http://")
        repeat(SOCKETS) {
            sockets +=
                client.newWebSocket(
                    Request.Builder().url(url).build(),
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            opened.incrementAndGet()
                            latch.countDown()
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            failed.incrementAndGet()
                            latch.countDown()
                        }
                    },
                )
        }

        val settled = latch.await(20, TimeUnit.SECONDS)
        // snapshot BEFORE cancelling: cancel() drives onFailure on every queued dial
        val openedCount = opened.get()
        val failedCount = failed.get()
        println(
            "\nmaxRequests=$CAP, dialed $SOCKETS websockets -> " +
                "opened=$openedCount failed=$failedCount allSettled=$settled",
        )

        sockets.forEach { it.cancel() }
        client.dispatcher.executorService.shutdown()

        // ANSWER: a live WebSocket holds its dispatcher slot for the socket's whole
        // lifetime. Exactly CAP open; the remaining SOCKETS-CAP sit queued forever and
        // never fail, so nothing surfaces the stall.
        //
        // Therefore `maxRequests` must NEVER be used to throttle the relay dial burst:
        // setting it to N would cap the app at N relays permanently. Throttling has to
        // happen above OkHttp, in RelayPool, where a settled dial can release its permit.
        assertEquals(CAP, openedCount, "only maxRequests websockets ever open")
        assertEquals(0, failedCount, "the queued dials never fail - they just hang")
        assertFalse(settled, "the remaining dials never settle")
    }
}
