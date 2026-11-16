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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.test.Test
import kotlin.time.measureTime

/**
 * Single-process load tests that report real numbers for:
 *   - WebSocket connection establishment rate
 *   - Concurrent subscription steady-state count
 *   - Live-event fanout latency to N subscribers
 *   - End-to-end EVENT publish throughput (EVENT → store → OK)
 *
 * Disabled by default — runs only when `runLoadBenchmark` system
 * property is set. Add `-DrunLoadBenchmark=true` to a Gradle test
 * invocation. Skipped under the normal test run because (a) numbers
 * vary on busy CI runners, and (b) some scenarios spin up thousands
 * of sockets which is rude on shared infra.
 */
class LoadBenchmark {
    private val enabled = System.getProperty("runLoadBenchmark") == "true"

    private inline fun benchmark(
        name: String,
        block: () -> Unit,
    ) {
        if (!enabled) {
            println("[skip] $name — set -DrunLoadBenchmark=true to enable")
            return
        }
        println("--- $name ---")
        block()
    }

    /**
     * How many concurrent WebSocket *connections* can we hold open?
     * Each test client opens a raw WS, sends one REQ, expects EOSE.
     * Stays connected after that.
     */
    @Test
    fun connectionsHeldOpen() =
        benchmark("connections held open") {
            for (target in listOf(100, 500, 1_000, 2_000, 5_000, 10_000)) {
                runBenchmarkServer { server, http ->
                    val httpUrl =
                        okhttp3.Request
                            .Builder()
                            .url(server.url.replace("ws://", "http://"))
                            .build()
                    val sockets = java.util.concurrent.CopyOnWriteArrayList<okhttp3.WebSocket>()
                    val opened = AtomicLong()
                    val gotEose = AtomicLong()
                    val opens =
                        measureTime {
                            repeat(target) {
                                val ws =
                                    http.newWebSocket(
                                        httpUrl,
                                        object : okhttp3.WebSocketListener() {
                                            override fun onOpen(
                                                webSocket: okhttp3.WebSocket,
                                                response: okhttp3.Response,
                                            ) {
                                                opened.incrementAndGet()
                                                webSocket.send(
                                                    """["REQ","s",{"kinds":[1],"limit":1}]""",
                                                )
                                            }

                                            override fun onMessage(
                                                webSocket: okhttp3.WebSocket,
                                                text: String,
                                            ) {
                                                if (text.startsWith("[\"EOSE\"")) {
                                                    gotEose.incrementAndGet()
                                                }
                                            }
                                        },
                                    )
                                sockets += ws
                            }
                            // Wait for either EOSE on every connection or 60s deadline.
                            val deadline = System.currentTimeMillis() + 60_000
                            while (gotEose.get() < target && System.currentTimeMillis() < deadline) {
                                Thread.sleep(50)
                            }
                        }
                    // Let activeSessionCount settle.
                    Thread.sleep(200)
                    println(
                        "target=$target opened=${opened.get()} eosed=${gotEose.get()} " +
                            "active=${server.activeSessionCount} elapsedMs=${opens.inWholeMilliseconds}",
                    )
                    sockets.forEach { runCatching { it.cancel() } }
                    if (gotEose.get() < target) {
                        println("  --> degradation at $target; stopping ramp-up")
                        return@runBenchmarkServer
                    }
                }
            }
        }

    /**
     * Holds 10 000 idle WebSocket connections open against a single
     * relay. Verifies that the adaptive outQueue (sketch A in
     * [connection-scaling plan][1]) lets us cross the ~2 000-connection
     * floor measured by [connectionsHeldOpen] without FD exhaustion or
     * runaway RSS.
     *
     * RUN PREREQ: requires a process FD limit ≥ ~12 000 (each WS uses
     * one FD on each side plus margin). On Linux: `ulimit -n 32768`
     * before launching the test JVM.
     *
     * [1]: geode/plans/2026-05-07-connection-scaling.md
     */
    @Test
    fun connectionsHeldOpen10k() =
        benchmark("connections held open 10k") {
            val target = 10_000
            runBenchmarkServer { server, http ->
                val httpUrl =
                    okhttp3.Request
                        .Builder()
                        .url(server.url.replace("ws://", "http://"))
                        .build()
                val sockets = java.util.concurrent.CopyOnWriteArrayList<okhttp3.WebSocket>()
                val opened = AtomicLong()
                val gotEose = AtomicLong()
                val opens =
                    measureTime {
                        repeat(target) {
                            val ws =
                                http.newWebSocket(
                                    httpUrl,
                                    object : okhttp3.WebSocketListener() {
                                        override fun onOpen(
                                            webSocket: okhttp3.WebSocket,
                                            response: okhttp3.Response,
                                        ) {
                                            opened.incrementAndGet()
                                            webSocket.send(
                                                """["REQ","s",{"kinds":[1],"limit":1}]""",
                                            )
                                        }

                                        override fun onMessage(
                                            webSocket: okhttp3.WebSocket,
                                            text: String,
                                        ) {
                                            if (text.startsWith("[\"EOSE\"")) {
                                                gotEose.incrementAndGet()
                                            }
                                        }
                                    },
                                )
                            sockets += ws
                        }
                        val deadline = System.currentTimeMillis() + 120_000
                        while (gotEose.get() < target && System.currentTimeMillis() < deadline) {
                            Thread.sleep(50)
                        }
                    }
                // JVM heap usage, not OS RSS — we can only measure
                // what the JVM itself has allocated. Force a GC first
                // so the reading reflects retained bytes, not in-flight
                // allocation churn from the connect ramp-up.
                val rt = Runtime.getRuntime()
                System.gc()
                Thread.sleep(200)
                val heapMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
                println(
                    "target=$target opened=${opened.get()} eosed=${gotEose.get()} " +
                        "active=${server.activeSessionCount} elapsedMs=${opens.inWholeMilliseconds} " +
                        "heapMb=$heapMb",
                )
                sockets.forEach { runCatching { it.cancel() } }
                check(gotEose.get() == target.toLong()) {
                    "expected $target EOSE but got ${gotEose.get()} — connection scaling regression"
                }
                check(heapMb < 1024) {
                    "JVM heap $heapMb MiB exceeded 1 GiB ceiling for $target idle connections"
                }
            }
        }

    /**
     * 5 000 idle subscribers, one publisher emitting 10 EPS for 10 s.
     * Measures fan-out latency at scale — exercises the queue path
     * for a connection that *does* fan out, not just an idle one.
     *
     * Each subscriber matches every published event (`kinds:[1]`),
     * so a single EVENT generates 5 000 outbound frames per tick.
     */
    @Test
    fun connectionsHeldOpenWithFanout() =
        benchmark("connections held open with fanout") {
            val subs = 5_000
            val durationSeconds = 10
            val targetEps = 10
            runBenchmarkServer { server, http ->
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                val subClient = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                val pubClient = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                try {
                    val relayUrl = server.url.normalizeRelayUrl()
                    val received = AtomicLong()
                    val eosed = AtomicLong()
                    // Last-receive timestamp per event id. The N-th
                    // subscriber to deliver wins; combined with the
                    // publish timestamp this gives us the full fan-out
                    // duration to the slowest subscriber.
                    val lastReceiveNs =
                        java.util.concurrent.ConcurrentHashMap<String, AtomicLong>()

                    repeat(subs) { i ->
                        subClient.subscribe(
                            "fanout-$i",
                            mapOf(relayUrl to listOf(Filter(kinds = listOf(1)))),
                            object : SubscriptionListener {
                                override suspend fun onEvent(
                                    event: com.vitorpamplona.quartz.nip01Core.core.Event,
                                    isLive: Boolean,
                                    relay: NormalizedRelayUrl,
                                    forFilters: List<Filter>?,
                                ) {
                                    lastReceiveNs
                                        .computeIfAbsent(event.id) { AtomicLong() }
                                        .set(System.nanoTime())
                                    received.incrementAndGet()
                                }

                                override fun onEose(
                                    relay: NormalizedRelayUrl,
                                    forFilters: List<Filter>?,
                                ) {
                                    eosed.incrementAndGet()
                                }
                            },
                        )
                    }

                    runBlocking {
                        withTimeout(120_000) {
                            while (eosed.get() < subs) kotlinx.coroutines.delay(100)
                        }
                    }
                    println("$subs subs ready; publishing ${targetEps * durationSeconds} events at $targetEps EPS...")

                    val signer = NostrSignerSync(KeyPair())
                    val publishedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
                    val totalEvents = targetEps * durationSeconds
                    val tickIntervalMs = 1000L / targetEps

                    runBlocking {
                        repeat(totalEvents) { i ->
                            val event = signer.sign(TextNoteEvent.build("fanout-$i"))
                            publishedAt[event.id] = System.nanoTime()
                            pubClient.publishAndConfirm(event, setOf(relayUrl))
                            kotlinx.coroutines.delay(tickIntervalMs)
                        }
                    }

                    // Wait for fan-out completion (or 30s, whichever first).
                    runBlocking {
                        withTimeout(30_000) {
                            while (received.get() < subs.toLong() * totalEvents) {
                                kotlinx.coroutines.delay(100)
                            }
                        }
                    }

                    val perEventLastMs =
                        lastReceiveNs.entries
                            .mapNotNull { (id, last) ->
                                publishedAt[id]?.let { (last.get() - it) / 1_000_000.0 }
                            }.sorted()
                    val p50 = perEventLastMs.getOrNull(perEventLastMs.size / 2) ?: -1.0
                    val p99 = perEventLastMs.getOrNull((perEventLastMs.size * 99) / 100) ?: -1.0
                    println(
                        "subs=$subs events=$totalEvents received=${received.get()}/${subs.toLong() * totalEvents} " +
                            "p50LastFanoutMs=${"%.1f".format(p50)} " +
                            "p99LastFanoutMs=${"%.1f".format(p99)}",
                    )
                } finally {
                    subClient.disconnect()
                    pubClient.disconnect()
                    scope.cancel()
                }
            }
        }

    /**
     * One publisher sends 10k events serially. Measures the round-trip
     * `EVENT` → `OK true` time, which is dominated by SQLite write
     * throughput + the write side of the policy stack.
     */
    @Test
    fun publishThroughputSingleClient() =
        benchmark("publish throughput single client") {
            runBenchmarkServer { server, http ->
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                val client = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                try {
                    val signer = NostrSignerSync(KeyPair())
                    val relayUrl = server.url.normalizeRelayUrl()

                    val n = 10_000
                    var ok = 0
                    val elapsed =
                        measureTime {
                            runBlocking {
                                repeat(n) { i ->
                                    val event = signer.sign(TextNoteEvent.build("hello $i"))
                                    if (client.publishAndConfirm(event, setOf(relayUrl))) ok++
                                }
                            }
                        }
                    val eps = (n * 1000.0) / elapsed.inWholeMilliseconds
                    println("events=$n ok=$ok elapsedMs=${elapsed.inWholeMilliseconds} eps=${"%.0f".format(eps)}")
                } finally {
                    client.disconnect()
                    scope.cancel()
                }
            }
        }

    /**
     * Same workload as [publishThroughputSingleClient] (sequential
     * publish-and-confirm on one connection) — kept as a regression
     * floor for the group-commit code path. Synchronous publishes
     * never coalesce in the writer (batch size is always 1), so the
     * EPS here measures per-event SQLite tx cost. The pipelined win
     * shows up in [publishPipelinedSingleClient].
     */
    @Test
    fun publishGroupCommitSingleClient() =
        benchmark("publish group-commit single client") {
            runBenchmarkServer { server, http ->
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                val client = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                try {
                    val signer = NostrSignerSync(KeyPair())
                    val relayUrl = server.url.normalizeRelayUrl()

                    val n = 10_000
                    var ok = 0
                    val elapsed =
                        measureTime {
                            runBlocking {
                                repeat(n) { i ->
                                    val event = signer.sign(TextNoteEvent.build("group-commit $i"))
                                    if (client.publishAndConfirm(event, setOf(relayUrl))) ok++
                                }
                            }
                        }
                    val eps = (n * 1000.0) / elapsed.inWholeMilliseconds
                    println(
                        "events=$n ok=$ok elapsedMs=${elapsed.inWholeMilliseconds} eps=${"%.0f".format(eps)}",
                    )
                    check(ok == n) { "expected all $n events accepted, got $ok" }
                    // Floor: the pre-batching baseline was ~760 EPS
                    // single-client (see plan). Anything below 500
                    // means the group-commit / ingest-queue rewrite
                    // regressed the synchronous path.
                    check(eps > 500) { "synchronous EPS $eps fell below the 500 floor" }
                } finally {
                    client.disconnect()
                    scope.cancel()
                }
            }
        }

    /**
     * One publisher, N subscribers. Publishes one EVENT and measures
     * fan-out latency: time from publish to last subscriber receiving.
     */
    @Test
    fun fanoutLatency() =
        benchmark("fanout latency") {
            for (subs in listOf(100, 500, 1_000, 2_000)) {
                runBenchmarkServer { server, http ->
                    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    val subClient = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                    val pubClient = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                    try {
                        val relayUrl = server.url.normalizeRelayUrl()
                        val received = AtomicLong()
                        val firstReceiveNs = AtomicLong(-1)
                        val lastReceiveNs = AtomicLong(-1)

                        // Set up `subs` subscribers.
                        val eosed = AtomicLong()
                        repeat(subs) { i ->
                            subClient.subscribe(
                                "fanout-$i",
                                mapOf(relayUrl to listOf(Filter(kinds = listOf(1)))),
                                object : SubscriptionListener {
                                    override suspend fun onEvent(
                                        event: com.vitorpamplona.quartz.nip01Core.core.Event,
                                        isLive: Boolean,
                                        relay: NormalizedRelayUrl,
                                        forFilters: List<Filter>?,
                                    ) {
                                        val now = System.nanoTime()
                                        firstReceiveNs.compareAndSet(-1, now)
                                        lastReceiveNs.set(now)
                                        received.incrementAndGet()
                                    }

                                    override fun onEose(
                                        relay: NormalizedRelayUrl,
                                        forFilters: List<Filter>?,
                                    ) {
                                        eosed.incrementAndGet()
                                    }
                                },
                            )
                        }

                        runBlocking {
                            withTimeout(60_000) {
                                while (eosed.get() < subs) kotlinx.coroutines.delay(50)
                            }
                        }
                        println("$subs subs ready; publishing one event...")

                        val signer = NostrSignerSync(KeyPair())
                        val event = signer.sign(TextNoteEvent.build("fanout"))
                        val publishStart = System.nanoTime()
                        runBlocking {
                            pubClient.publishAndConfirm(event, setOf(relayUrl))
                        }
                        val publishEnd = System.nanoTime()

                        // Wait for fanout to complete.
                        runBlocking {
                            withTimeout(60_000) {
                                while (received.get() < subs) kotlinx.coroutines.delay(10)
                            }
                        }

                        val firstFanoutMs = (firstReceiveNs.get() - publishStart) / 1_000_000.0
                        val lastFanoutMs = (lastReceiveNs.get() - publishStart) / 1_000_000.0
                        val publishMs = (publishEnd - publishStart) / 1_000_000.0
                        println(
                            "subs=$subs publishMs=${"%.1f".format(publishMs)} " +
                                "fanoutFirstMs=${"%.1f".format(firstFanoutMs)} " +
                                "fanoutLastMs=${"%.1f".format(lastFanoutMs)} " +
                                "received=${received.get()}/$subs",
                        )
                    } finally {
                        subClient.disconnect()
                        pubClient.disconnect()
                        scope.cancel()
                    }
                }
            }
        }

    /**
     * Selective fanout: each event matches exactly ONE of N
     * subscribers. Stresses the inverted-index path in
     * `FilterIndex` / `LiveEventStore`: without an index, the relay
     * walks all N subscribers per event and runs `Filter.match` to
     * find the one true match; with the index, an author-keyed
     * lookup returns the single subscriber directly.
     *
     * Different from [fanoutLatency], which tests broadcast fanout
     * (one event reaches every subscriber). Here per-event work is
     * lookup-bound rather than delivery-bound, so latency should be
     * roughly **flat** in N once the index is wired up — that's the
     * shape change the index is meant to deliver.
     *
     * Configurable via system properties:
     *  - `fanoutScalingSubs`: comma-separated list of N values
     *    (default `100,1000,5000`).
     *  - `fanoutScalingEvents`: total events per N (default scales
     *    inversely with N to keep test wall time bounded — at 5000
     *    subs the OkHttp WebSocket dispatcher on the test client
     *    starts serializing inbound EVENT frames at high throughput,
     *    which inflates measured latency without telling us anything
     *    about the index itself).
     */
    @Test
    fun fanoutScaling() =
        benchmark("fanout scaling") {
            val subSweep =
                System
                    .getProperty("fanoutScalingSubs")
                    ?.split(",")
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOf(100, 1_000, 5_000)
            for (subs in subSweep) {
                runBenchmarkServer { server, http ->
                    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    val subClient = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                    val pubClient = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                    try {
                        val relayUrl = server.url.normalizeRelayUrl()

                        // One keypair per subscriber. Each subscriber
                        // filters on its own author so the relay can
                        // route an event to exactly one sub via the
                        // FilterIndex's byAuthor bucket.
                        val keys = List(subs) { KeyPair() }
                        val pubkeys = keys.map { it.pubKey.toHexKey() }
                        val signers = keys.map { NostrSignerSync(it) }

                        // Per-subscriber arrival timestamp. We
                        // overwrite per round; the publish path waits
                        // until the slot is non-zero before moving on.
                        val arrivalNs = AtomicLongArray(subs)
                        val received = AtomicLong()
                        val eosed = AtomicLong()

                        repeat(subs) { i ->
                            subClient.subscribe(
                                "scale-$i",
                                mapOf(
                                    relayUrl to
                                        listOf(
                                            Filter(
                                                authors = listOf(pubkeys[i]),
                                                kinds = listOf(1),
                                            ),
                                        ),
                                ),
                                object : SubscriptionListener {
                                    override suspend fun onEvent(
                                        event: com.vitorpamplona.quartz.nip01Core.core.Event,
                                        isLive: Boolean,
                                        relay: NormalizedRelayUrl,
                                        forFilters: List<Filter>?,
                                    ) {
                                        arrivalNs.set(i, System.nanoTime())
                                        received.incrementAndGet()
                                    }

                                    override fun onEose(
                                        relay: NormalizedRelayUrl,
                                        forFilters: List<Filter>?,
                                    ) {
                                        eosed.incrementAndGet()
                                    }
                                },
                            )
                        }

                        runBlocking {
                            withTimeout(120_000) {
                                while (eosed.get() < subs) kotlinx.coroutines.delay(100)
                            }
                        }

                        // Events round-robin over the N pubkeys. Per
                        // the plan we'd like 10k everywhere, but
                        // [WebSocketSessionPump.MAX_OUTGOING_BUFFER]
                        // (8192 frames) on the relay's per-session
                        // outbound queue trips when the subClient's
                        // single-WS read pipeline can't drain
                        // (subs + events) frames fast enough. That's
                        // a test-infra ceiling, not the index's. We
                        // back off events at high N so the latency
                        // numbers reflect dispatch cost rather than
                        // backpressure-driven connection teardown.
                        // Override with -DfanoutScalingEvents=N to
                        // force a value (caller's responsibility to
                        // stay below the pump cap).
                        val totalEvents =
                            System
                                .getProperty("fanoutScalingEvents")
                                ?.toIntOrNull()
                                ?: when {
                                    subs <= 200 -> 2_000
                                    subs <= 1_000 -> 2_000
                                    subs <= 2_000 -> 1_000
                                    else -> 1_000
                                }
                        println("$subs subs ready; publishing $totalEvents events round-robin...")

                        val latenciesMs = DoubleArray(totalEvents)

                        runBlocking {
                            val start = System.nanoTime()
                            for (i in 0 until totalEvents) {
                                val target = i % subs
                                val event = signers[target].sign(TextNoteEvent.build("scale-$i"))
                                arrivalNs.set(target, 0)
                                val pubStart = System.nanoTime()
                                pubClient.publishAndConfirm(event, setOf(relayUrl))
                                withTimeout(30_000) {
                                    while (arrivalNs.get(target) == 0L) kotlinx.coroutines.delay(1)
                                }
                                latenciesMs[i] = (arrivalNs.get(target) - pubStart) / 1_000_000.0
                            }
                            val totalMs = (System.nanoTime() - start) / 1_000_000.0

                            val sorted = latenciesMs.copyOf().also { it.sort() }
                            val p50 = sorted[sorted.size / 2]
                            val p99 = sorted[(sorted.size * 99) / 100]
                            val p999 = sorted[(sorted.size * 999) / 1000]
                            val mean = sorted.average()
                            println(
                                "subs=$subs events=$totalEvents totalMs=${"%.0f".format(totalMs)} " +
                                    "meanMs=${"%.2f".format(mean)} " +
                                    "p50Ms=${"%.2f".format(p50)} " +
                                    "p99Ms=${"%.2f".format(p99)} " +
                                    "p999Ms=${"%.2f".format(p999)} " +
                                    "received=${received.get()}/$totalEvents",
                            )
                        }
                    } finally {
                        subClient.disconnect()
                        pubClient.disconnect()
                        scope.cancel()
                    }
                }
            }
        }

    /**
     * One publisher fires N EVENTs back-to-back without awaiting
     * intermediate OKs, then collects all OKs by event id. This is
     * the workload that exercises Tier 2 (per-connection ingest
     * pipeline) + Tier 1 (group commit) together — multiple events
     * are in flight on the same connection, so the writer can batch.
     *
     * Verifies the relaxed OK contract: every event id receives
     * exactly one OK frame, in any order.
     */
    @Test
    fun publishPipelinedSingleClient() =
        benchmark("publish pipelined single client") {
            runBenchmarkServer { server, http ->
                val n = 10_000
                val signer = NostrSignerSync(KeyPair())
                val events =
                    runBlocking {
                        (0 until n).map { i ->
                            signer.sign(TextNoteEvent.build("pipe $i"))
                        }
                    }
                val ids = events.mapTo(HashSet()) { it.id }

                val httpUrl =
                    okhttp3.Request
                        .Builder()
                        .url(server.url.replace("ws://", "http://"))
                        .build()
                val okSeen = AtomicLong()
                val okFailures = AtomicLong()
                val unknownIds = AtomicLong()
                val seenIds =
                    java.util.concurrent.ConcurrentHashMap
                        .newKeySet<String>()
                val done = java.util.concurrent.CountDownLatch(1)

                val ws =
                    http.newWebSocket(
                        httpUrl,
                        object : okhttp3.WebSocketListener() {
                            override fun onMessage(
                                webSocket: okhttp3.WebSocket,
                                text: String,
                            ) {
                                if (!text.startsWith("[\"OK\"")) return
                                // ["OK","<id>",true|false,"<reason>"] —
                                // a tiny string scan is enough for a
                                // bench. Index 6 is past `["OK","`.
                                val idStart = 7
                                val idEnd = text.indexOf('"', idStart)
                                if (idEnd <= idStart) return
                                val id = text.substring(idStart, idEnd)
                                if (!ids.contains(id)) {
                                    unknownIds.incrementAndGet()
                                    return
                                }
                                if (!seenIds.add(id)) return
                                if (text.contains(",true,")) {
                                    okSeen.incrementAndGet()
                                } else {
                                    okFailures.incrementAndGet()
                                }
                                if (okSeen.get() + okFailures.get() == n.toLong()) done.countDown()
                            }
                        },
                    )

                val elapsed =
                    measureTime {
                        // Burst-send: queue every EVENT to OkHttp's
                        // outbound buffer without any await, then
                        // wait for the corresponding OK frames.
                        for (event in events) {
                            ws.send("""["EVENT",${event.toJson()}]""")
                        }
                        check(done.await(60, java.util.concurrent.TimeUnit.SECONDS)) {
                            "timed out waiting for OKs: ok=${okSeen.get()} rej=${okFailures.get()} unknown=${unknownIds.get()}"
                        }
                    }
                val eps = (n * 1000.0) / elapsed.inWholeMilliseconds
                println(
                    "events=$n ok=${okSeen.get()} rejected=${okFailures.get()} " +
                        "unknownIds=${unknownIds.get()} elapsedMs=${elapsed.inWholeMilliseconds} eps=${"%.0f".format(eps)}",
                )
                check(okSeen.get() == n.toLong()) {
                    "expected $n accepted OKs, got ${okSeen.get()} (rejected ${okFailures.get()})"
                }
                check(seenIds.size == n) {
                    "expected $n unique OK ids, got ${seenIds.size} — duplicate or missing OKs"
                }
                ws.cancel()
            }
        }

    /**
     * Many concurrent publishers, each on their own WebSocket. Tells
     * us whether the SQLite single-writer bottleneck is the floor or
     * if there's contention upstream.
     */
    @Test
    fun publishThroughputConcurrent() =
        benchmark("publish throughput concurrent") {
            for (parallel in listOf(2, 4, 8, 16, 32)) {
                runBenchmarkServer { server, http ->
                    val total = 5_000
                    val perThread = total / parallel
                    val ok = AtomicLong()
                    val elapsed =
                        measureTime {
                            val threads =
                                (0 until parallel).map { tid ->
                                    Thread {
                                        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                                        val client = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
                                        try {
                                            val signer = NostrSignerSync(KeyPair())
                                            val relayUrl = server.url.normalizeRelayUrl()
                                            runBlocking {
                                                repeat(perThread) { i ->
                                                    val ev = signer.sign(TextNoteEvent.build("hi-$tid-$i"))
                                                    if (client.publishAndConfirm(ev, setOf(relayUrl))) ok.incrementAndGet()
                                                }
                                            }
                                        } finally {
                                            client.disconnect()
                                            scope.cancel()
                                        }
                                    }.also { it.start() }
                                }
                            threads.forEach { it.join() }
                        }
                    val eps = (ok.get() * 1000.0) / elapsed.inWholeMilliseconds
                    println(
                        "parallel=$parallel total=${ok.get()}/$total elapsedMs=${elapsed.inWholeMilliseconds} eps=${"%.0f".format(eps)}",
                    )
                }
            }
        }

    /** Spin up an isolated relay + http client per scenario. */
    private inline fun runBenchmarkServer(block: (KtorRelay, OkHttpClient) -> Unit) {
        val placeholder = "ws://127.0.0.1:7771/".normalizeRelayUrl()
        val relay = RelayEngine(url = placeholder)
        val server = KtorRelay(relay, host = "127.0.0.1", port = 0).start()
        val http =
            OkHttpClient
                .Builder()
                // Don't bottleneck on the OkHttp dispatcher when we
                // open thousands of WS connections from one client.
                .dispatcher(
                    okhttp3.Dispatcher().apply {
                        maxRequests = 100_000
                        maxRequestsPerHost = 100_000
                    },
                ).build()
        try {
            block(server, http)
        } finally {
            http.dispatcher.executorService.shutdownNow()
            server.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
            relay.close()
        }
    }
}
