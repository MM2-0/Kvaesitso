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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.TcpNoDelaySocketFactory
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.EventFactory
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Splits the wire-level small-REQ floor into transport vs relay work,
 * over the production stack (Ktor CIO server, OkHttp client, loopback).
 *
 *  - echo-1 / echo-22: a bare Ktor CIO websocket replying with 1 / 22
 *    ~250 B frames — the transport stack's round-trip floor; the burst
 *    variants show per-frame cost is negligible.
 *  - echo external / ext+1ms: replies produced from a foreign coroutine,
 *    immediately or after the connection loop parks — proves cross-context
 *    sends into CIO are prompt.
 *  - geode REQ / NOTICE / empty REQ / inproc: the relay's own layers.
 *
 * Historical note — this benchmark found the TcpNoDelaySocketFactory
 * issue: without TCP_NODELAY on the client, a CLOSE (which relays never
 * answer) followed by a REQ nagles the REQ behind the unACKed CLOSE for
 * the ~40 ms delayed-ACK window, measured here as a flat 43.7 ms per
 * round. With the factory (now used by every production client), geode's
 * ~21-row REQ costs ~1.2 ms on the wire — matching relayBench — of which
 * ~0.6 ms is the transport floor and ~0.5 ms the per-REQ server work
 * documented in quartz/plans/2026-07-04-small-req-floor.md.
 */
class WireReqFloorBenchmark {
    companion object {
        const val EVENTS = 50_000
        const val AUTHORS = 2_500
        const val ROUNDS = 300
        const val WARMUP = 50
        const val BURST = 22
    }

    private fun hexId(seed: Int): String = seed.toString(16).padStart(64, '0')

    private fun pubkey(seed: Int): String = (seed % AUTHORS).toString(16).padStart(64, 'a')

    private val sig = "0".repeat(128)

    private fun event(seed: Int): Event =
        EventFactory.create(
            id = hexId(seed),
            pubKey = pubkey(seed),
            createdAt = 1_600_000_000L + (seed * 7919) % 1_000_000,
            kind = 1,
            tags = emptyArray(),
            content = "wire req floor benchmark $seed",
            sig = sig,
        )

    private fun median(samples: LongArray): Double {
        samples.sort()
        return samples[samples.size / 2] / 1e6
    }

    /** Blocking one-connection driver: send [request], await [expectedFrames] replies. */
    private class Driver(
        client: OkHttpClient,
        url: String,
    ) {
        private val frames = ArrayBlockingQueue<String>(4096)
        val socket: WebSocket

        init {
            val opened = ArrayBlockingQueue<Boolean>(1)
            socket =
                client.newWebSocket(
                    Request.Builder().url(url).build(),
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            opened.put(true)
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            frames.put(text)
                        }
                    },
                )
            check(opened.poll(10, TimeUnit.SECONDS) == true) { "websocket did not open" }
        }

        var lastFirstFrameNanos: Long = 0
            private set

        fun roundTrip(
            request: String,
            expectedFrames: Int,
        ): Long {
            val t0 = System.nanoTime()
            socket.send(request)
            repeat(expectedFrames) { i ->
                checkNotNull(frames.poll(10, TimeUnit.SECONDS)) { "timed out waiting for frame" }
                if (i == 0) lastFirstFrameNanos = System.nanoTime() - t0
            }
            return System.nanoTime() - t0
        }
    }

    @Test
    fun transportVsRelayFloor() =
        runBlocking {
            // TCP_NODELAY, via the same factory production clients use:
            // without it, a CLOSE (never answered) followed by a REQ nagles
            // the REQ behind the unACKed CLOSE for the ~40 ms delayed-ACK
            // window — this benchmark measured a flat 43.7 ms per round
            // before the factory existed, which is how the issue was found.
            val client = OkHttpClient.Builder().socketFactory(TcpNoDelaySocketFactory).build()

            // --- bare Ktor CIO echo server: N frames of ~250 B per request ---
            val payload = "x".repeat(250)
            val echo =
                embeddedServer(CIO, port = 0) {
                    install(WebSockets)
                    routing {
                        webSocket("/") {
                            for (frame in incoming) {
                                if (frame !is Frame.Text) continue
                                val text = frame.readText()
                                if (text.startsWith("x")) {
                                    // Reply from an EXTERNAL coroutine — the
                                    // cross-context path geode's launched REQ
                                    // handler uses.
                                    val n = text.drop(1).toIntOrNull() ?: 1
                                    launch(Dispatchers.Default) {
                                        repeat(n) { outgoing.send(Frame.Text(payload)) }
                                    }
                                } else if (text.startsWith("d")) {
                                    // Same, but AFTER the connection loop has
                                    // gone idle — does a parked CIO connection
                                    // pick up a cross-thread send promptly?
                                    val n = text.drop(1).toIntOrNull() ?: 1
                                    launch(Dispatchers.Default) {
                                        delay(1)
                                        repeat(n) { outgoing.send(Frame.Text(payload)) }
                                    }
                                } else {
                                    val n = text.toIntOrNull() ?: 1
                                    repeat(n) { outgoing.send(Frame.Text(payload)) }
                                }
                            }
                        }
                    }
                }
            echo.start(wait = false)
            val echoPort =
                echo.engine
                    .resolvedConnectors()
                    .first()
                    .port

            val echoDriver = Driver(client, "ws://127.0.0.1:$echoPort/")
            repeat(WARMUP) { echoDriver.roundTrip("1", 1) }
            val echo1 = LongArray(ROUNDS) { echoDriver.roundTrip("1", 1) }
            repeat(WARMUP) { echoDriver.roundTrip("$BURST", BURST) }
            val echoN = LongArray(ROUNDS) { echoDriver.roundTrip("$BURST", BURST) }
            repeat(WARMUP) { echoDriver.roundTrip("x1", 1) }
            val echoExternal = LongArray(ROUNDS) { echoDriver.roundTrip("x1", 1) }
            repeat(WARMUP) { echoDriver.roundTrip("d1", 1) }
            val echoDelayed = LongArray(ROUNDS) { echoDriver.roundTrip("d1", 1) }

            // --- geode: real REQ over the same stack ---
            // Same store setup SmallReqFloorBenchmark proved answers this
            // filter in ~0.18 ms in-process — this benchmark attributes
            // the wire-vs-in-process delta, so the storage side must be
            // the known-fast configuration.
            val store = EventStore(dbName = null, indexStrategy = DefaultIndexingStrategy(indexEventsByPubkeyAlone = true))
            (1..EVENTS).chunked(2000).forEach { chunk -> store.batchInsert(chunk.map { event(it) }) }
            val relay = RelayEngine(url = "ws://127.0.0.1:7795/".normalizeRelayUrl(), store = store, parentContext = Dispatchers.IO + SupervisorJob())
            val server = KtorRelay(relay, host = "127.0.0.1", port = 0).start()

            val geodeDriver = Driver(client, server.url)

            fun req(round: Int) = """["REQ","w$round",{"authors":["${pubkey(round)}"],"kinds":[1],"limit":50}]"""

            // Each REQ answers with (rows + 1) frames — the EVENTs then
            // EOSE — and the row count is author-dependent, so resolve
            // every round's expected count from the store up front. Subs
            // are CLOSEd after each round (silently, per NIP-01) so the
            // relay's live-subscription registry doesn't grow with the
            // round count and skew later samples.
            suspend fun rowsFor(round: Int): Int = store.query<Event>(Filter(authors = listOf(pubkey(round)), kinds = listOf(1), limit = 50)).size

            repeat(WARMUP) { round ->
                val rows = rowsFor(round)
                geodeDriver.roundTrip(req(round), rows + 1)
                geodeDriver.socket.send("""["CLOSE","w$round"]""")
            }
            val geodeSamples = LongArray(ROUNDS)
            val geodeFirst = LongArray(ROUNDS)
            var totalRows = 0
            for (i in 0 until ROUNDS) {
                val round = WARMUP + i
                val rows = rowsFor(round)
                totalRows += rows
                geodeSamples[i] = geodeDriver.roundTrip(req(round), rows + 1)
                geodeFirst[i] = geodeDriver.lastFirstFrameNanos
                geodeDriver.socket.send("""["CLOSE","w$round"]""")
            }

            // Probe A: malformed frame -> NOTICE, answered inline on the
            // pump coroutine (no launch, no SQL). Probe B: REQ matching
            // nothing -> lone EOSE (launch + SQL, no rows).
            repeat(WARMUP) { geodeDriver.roundTrip("not json", 1) }
            val notice = LongArray(ROUNDS) { geodeDriver.roundTrip("not json", 1) }
            repeat(WARMUP) { i ->
                geodeDriver.roundTrip("""["REQ","n$i",{"ids":["${"f".repeat(64)}"]}]""", 1)
                geodeDriver.socket.send("""["CLOSE","n$i"]""")
            }
            val emptyReq =
                LongArray(ROUNDS) { i ->
                    val t = geodeDriver.roundTrip("""["REQ","m$i",{"ids":["${"f".repeat(64)}"]}]""", 1)
                    geodeDriver.socket.send("""["CLOSE","m$i"]""")
                    t
                }
            // Same probe with NO CLOSE between rounds: if this is fast, the
            // 44 ms is the client's CLOSE frame sitting unACKed (server
            // sends nothing for a CLOSE) and Nagle holding the next REQ
            // behind it until the ~40 ms delayed ACK — a client-side TCP
            // artifact, not the relay.
            repeat(WARMUP) { i -> geodeDriver.roundTrip("""["REQ","nc$i",{"ids":["${"a".repeat(64)}"]}]""", 1) }
            val emptyNoClose =
                LongArray(ROUNDS) { i ->
                    geodeDriver.roundTrip("""["REQ","ncm$i",{"ids":["${"a".repeat(64)}"]}]""", 1)
                }

            // Probe C: same RelayEngine, no wire — an in-process session
            // while Ktor keeps serving. Splits engine-state issues from
            // transport-adjacent ones.
            val inproc = LongArray(ROUNDS)
            run {
                val q = ArrayBlockingQueue<String>(4096)
                val session = relay.server.connect { q.put(it) }
                repeat(WARMUP) { i ->
                    session.receive("""["REQ","p$i",{"ids":["${"e".repeat(64)}"]}]""")
                    checkNotNull(q.poll(10, TimeUnit.SECONDS))
                    session.receive("""["CLOSE","p$i"]""")
                }
                for (i in 0 until ROUNDS) {
                    val t0 = System.nanoTime()
                    session.receive("""["REQ","q$i",{"ids":["${"e".repeat(64)}"]}]""")
                    checkNotNull(q.poll(10, TimeUnit.SECONDS))
                    inproc[i] = System.nanoTime() - t0
                    session.receive("""["CLOSE","q$i"]""")
                }
                session.close()
            }

            assertTrue(totalRows > 0)
            println("WireReqFloorBenchmark (loopback, OkHttp client) @ ${EVENTS / 1000}k events, medians of $ROUNDS")
            println("  echo 1 frame:            ${"%6.3f".format(median(echo1))} ms")
            println("  echo $BURST frames:          ${"%6.3f".format(median(echoN))} ms")
            println("  echo 1 frame (external):  ${"%6.3f".format(median(echoExternal))} ms")
            println("  echo 1 frame (ext+1ms):   ${"%6.3f".format(median(echoDelayed))} ms")
            println("  geode REQ (~${totalRows / ROUNDS} rows+EOSE): ${"%6.3f".format(median(geodeSamples))} ms (first frame ${"%6.3f".format(median(geodeFirst))} ms)")
            println("  geode NOTICE (inline):    ${"%6.3f".format(median(notice))} ms")
            println("  geode empty REQ (launch): ${"%6.3f".format(median(emptyReq))} ms")
            println("  geode empty REQ no CLOSE: ${"%6.3f".format(median(emptyNoClose))} ms")
            println("  geode empty REQ (inproc): ${"%6.3f".format(median(inproc))} ms")

            geodeDriver.socket.close(1000, null)
            echoDriver.socket.close(1000, null)
            server.stop(0, 1_000)
            relay.close()
            echo.stop(0, 500)
            client.dispatcher.executorService.shutdown()
        }
}
