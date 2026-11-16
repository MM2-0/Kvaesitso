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
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropySyncException
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySync
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySyncFanOut
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test

/**
 * Answers: "we need to download N-million events from a single relay as fast
 * as possible — considering just download and parsing, what helps?"
 *
 * Three parts:
 *
 * 1. LOCAL CEILINGS — a real geode relay on a localhost TCP port, seeded with
 *    [LOCAL_EVENTS] events, so network bandwidth and server latency are ~free
 *    and the client stack is what's measured. Variants: 1 connection vs
 *    [SHARDS] connections with the `created_at` range sharded across them;
 *    single giant REQ vs realistic 1000/page cursor pagination (a second
 *    server instance clamps limits to force paging); and the full quartz
 *    stack (parse + dispatch) vs a raw OkHttp socket that only counts frames
 *    (no parse), which isolates the parse share of the pipeline.
 *
 * 2. OFFLINE PARSE STRATEGIES — what to do with each frame: full
 *    `fromJsonToMessage`, full parse fanned across cores, or an id-only scan
 *    (the "archive the raw frame now, parse lazily later" strategy).
 *
 * 3. PRODUCTION TEST CASE — NIP-77 negentropy sync of `kinds:[30382]` from
 *    nip85.nosfabrica.com (capped at [PROD_MAX_EVENTS]) against plain
 *    until-cursor paging of the same filter. Negentropy enumerates the id set
 *    server-side and downloads by id-batch with 8 concurrent REQs on one
 *    connection — the protocol-level answer to serial page round-trips.
 *
 * Gated behind PROD_RELAY_BENCH (same as ProductionReceiverBenchmark):
 *   ./gradlew :geode:test --tests "*.BulkDownloadBenchmark" -PprodRelayBench=1
 */
class BulkDownloadBenchmark {
    companion object {
        const val LOCAL_EVENTS = 100_000
        const val SHARDS = 4
        const val PAGE_LIMIT = 1_000
        const val LOCAL_PAGE_TIMEOUT_MS = 120_000L

        const val PROD_RELAY = "wss://nip85.nosfabrica.com"
        const val PROD_KIND = 30382
        const val PROD_MAX_EVENTS = 20_000
    }

    private fun report(
        name: String,
        events: Long,
        bytes: Long,
        wallNanos: Long,
    ) {
        println(
            "  %-34s %,8d events  %6.1f MB  wall=%6.0fms  -> %,8.0f events/s  %5.1f MB/s"
                .format(
                    name,
                    events,
                    bytes / 1e6,
                    wallNanos / 1e6,
                    events * 1e9 / wallNanos,
                    bytes * 1e3 / wallNanos,
                ),
        )
    }

    // ------------------------------------------------------------------
    // Part 1: local ceilings against a seeded geode relay on localhost TCP
    // ------------------------------------------------------------------

    /** Splits [1, LOCAL_EVENTS] (the seeded createdAt space) into [shards] windows. */
    private fun windows(shards: Int): List<Pair<Long, Long>> {
        val step = LOCAL_EVENTS / shards
        return (0 until shards).map { i ->
            val lo = i * step + 1L
            val hi = if (i == shards - 1) LOCAL_EVENTS.toLong() else (i + 1) * step.toLong()
            lo to hi
        }
    }

    /** Full quartz stack: NostrClient + fetchAllPages, one client per shard. */
    private suspend fun quartzDownload(
        name: String,
        relayUrl: NormalizedRelayUrl,
        httpClient: OkHttpClient,
        shards: Int,
    ) {
        val count = AtomicLong(0)
        val bytes = AtomicLong(0)
        val start = System.nanoTime()
        coroutineScope {
            windows(shards)
                .map { (lo, hi) ->
                    async(Dispatchers.IO) {
                        val client = NostrClient(BasicOkHttpWebSocket.Builder { httpClient })
                        try {
                            client.fetchAllPages(
                                relay = relayUrl,
                                filters = listOf(Filter(kinds = listOf(1), since = lo, until = hi)),
                                idleTimeoutMs = LOCAL_PAGE_TIMEOUT_MS,
                            ) { event ->
                                count.incrementAndGet()
                                bytes.addAndGet(event.content.length.toLong())
                            }
                        } finally {
                            client.close()
                        }
                    }
                }.awaitAll()
        }
        val wall = System.nanoTime() - start
        report(name, count.get(), bytes.get(), wall)
        if (count.get() != LOCAL_EVENTS.toLong()) {
            println("    !! expected $LOCAL_EVENTS events, got ${count.get()}")
        }
    }

    /**
     * Raw OkHttp socket per shard: counts EVENT frames, no JSON parse, no
     * quartz client. The download-only floor the parse stage sits on top of.
     */
    private fun rawDownload(
        name: String,
        serverWsUrl: String,
        httpClient: OkHttpClient,
        shards: Int,
    ) {
        val count = AtomicLong(0)
        val bytes = AtomicLong(0)
        val latch = CountDownLatch(shards)
        val start = System.nanoTime()

        val sockets =
            windows(shards).mapIndexed { i, (lo, hi) ->
                httpClient.newWebSocket(
                    Request.Builder().url(serverWsUrl).build(),
                    object : okhttp3.WebSocketListener() {
                        override fun onOpen(
                            webSocket: okhttp3.WebSocket,
                            response: Response,
                        ) {
                            webSocket.send("""["REQ","raw$i",{"kinds":[1],"since":$lo,"until":$hi}]""")
                        }

                        override fun onMessage(
                            webSocket: okhttp3.WebSocket,
                            text: String,
                        ) {
                            if (text.startsWith("[\"EVENT\"")) {
                                count.incrementAndGet()
                                bytes.addAndGet(text.length.toLong())
                            } else if (text.startsWith("[\"EOSE\"")) {
                                latch.countDown()
                            }
                        }

                        override fun onFailure(
                            webSocket: okhttp3.WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            latch.countDown()
                        }
                    },
                )
            }

        latch.await(120, TimeUnit.SECONDS)
        val wall = System.nanoTime() - start
        sockets.forEach { it.cancel() }
        report(name, count.get(), bytes.get(), wall)
    }

    private fun localCeilings(httpClient: OkHttpClient) {
        println("\n=== LOCAL CEILINGS: geode on localhost, $LOCAL_EVENTS seeded events ===")

        val placeholderA = "ws://127.0.0.1:7771/".normalizeRelayUrl()
        val placeholderB = "ws://127.0.0.1:7772/".normalizeRelayUrl()

        val engine = RelayEngine(url = placeholderA)
        val seedNanos =
            runBlocking {
                val events =
                    (1..LOCAL_EVENTS).map {
                        SyntheticEvents.fakeEvent(
                            idSeed = it,
                            kind = 1,
                            pubKey = SyntheticEvents.hexId(it % 1000 + 1),
                            createdAt = it.toLong(),
                            content = "bulk download benchmark payload $it ".repeat(6),
                        )
                    }
                val t = System.nanoTime()
                events.chunked(2_000).forEach { engine.store.batchInsert(it) }
                System.nanoTime() - t
            }
        println("  (seeded in %.1fs)".format(seedNanos / 1e9))

        // Same store, second engine that clamps every REQ to PAGE_LIMIT — forces
        // fetchAllPages into realistic until-cursor pagination.
        val pagedEngine =
            RelayEngine(
                url = placeholderB,
                store = engine.store,
                policyBuilder = { LimitsPolicy(RelayLimits(defaultLimit = PAGE_LIMIT, maxLimit = PAGE_LIMIT)) },
            )

        val openServer = KtorRelay(engine, port = 0).start()
        val pagedServer = KtorRelay(pagedEngine, port = 0).start()
        try {
            val openUrl = openServer.url.normalizeRelayUrl()
            val pagedUrl = pagedServer.url.normalizeRelayUrl()

            runBlocking {
                rawDownload("raw 1-conn single-REQ (no parse)", openServer.url, httpClient, shards = 1)
                rawDownload("raw $SHARDS-conn sharded (no parse)", openServer.url, httpClient, shards = SHARDS)
                quartzDownload("quartz 1-conn single-REQ", openUrl, httpClient, shards = 1)
                quartzDownload("quartz $SHARDS-conn sharded", openUrl, httpClient, shards = SHARDS)
                quartzDownload("quartz 1-conn paged $PAGE_LIMIT", pagedUrl, httpClient, shards = 1)
                quartzDownload("quartz $SHARDS-conn sharded+paged", pagedUrl, httpClient, shards = SHARDS)
            }
        } finally {
            openServer.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
            pagedServer.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
            pagedEngine.close()
            engine.close()
        }
    }

    // ------------------------------------------------------------------
    // Part 2: offline per-frame parse strategies
    // ------------------------------------------------------------------

    private fun offlineParseStrategies() {
        println("\n=== OFFLINE: per-frame strategies (50k synthetic frames) ===")
        val frames =
            (1..50_000).map {
                val event =
                    SyntheticEvents.fakeEvent(
                        idSeed = it,
                        kind = 1,
                        pubKey = SyntheticEvents.hexId(it % 1000 + 1),
                        createdAt = it.toLong(),
                        content = "bulk download benchmark payload $it ".repeat(6),
                    )
                """["EVENT","sub",${event.toJson()}]"""
            }
        val totalBytes = frames.sumOf { it.length.toLong() }

        // warmup
        frames.take(5_000).forEach { OptimizedJsonMapper.fromJsonToMessage(it) }

        var t = System.nanoTime()
        frames.forEach { OptimizedJsonMapper.fromJsonToMessage(it) }
        report("full parse, 1 thread", frames.size.toLong(), totalBytes, System.nanoTime() - t)

        val cores = Runtime.getRuntime().availableProcessors()
        t = System.nanoTime()
        runBlocking(Dispatchers.Default) {
            frames
                .chunked((frames.size + cores - 1) / cores)
                .map { chunk -> async { chunk.forEach { OptimizedJsonMapper.fromJsonToMessage(it) } } }
                .awaitAll()
        }
        report("full parse, $cores threads", frames.size.toLong(), totalBytes, System.nanoTime() - t)

        // id-only scan: what "write the raw frame to disk now, parse lazily
        // later" pays per frame to dedup/route.
        var sink = 0
        t = System.nanoTime()
        frames.forEach { frame ->
            val i = frame.indexOf("\"id\":\"")
            if (i >= 0) sink += frame[i + 6].code
        }
        report("id-scan only (archive raw)", frames.size.toLong(), totalBytes, System.nanoTime() - t)
        check(sink != 0)
    }

    // ------------------------------------------------------------------
    // Part 3: production — negentropy vs paging for kind 30382 on nosfabrica
    // ------------------------------------------------------------------

    private suspend fun productionNegentropy(httpClient: OkHttpClient) {
        println("\n=== PRODUCTION: $PROD_RELAY kinds=[$PROD_KIND], capped at $PROD_MAX_EVENTS events ===")
        val relay = PROD_RELAY.normalizeRelayUrl()

        // A) NIP-77 negentropy: server enumerates the id set, we download by
        //    id-batches with 8 concurrent REQs on the same connection.
        run {
            val client = NostrClient(BasicOkHttpWebSocket.Builder { httpClient })
            try {
                val count = AtomicLong(0)
                val bytes = AtomicLong(0)
                val start = System.nanoTime()
                val result =
                    client.negentropySync(
                        relay = relay,
                        filter = Filter(kinds = listOf(PROD_KIND)),
                        maxEvents = PROD_MAX_EVENTS,
                        onProgress = { need, downloaded ->
                            if (downloaded % 5_000 == 0 && downloaded > 0) {
                                println("    … negentropy progress: need=$need downloaded=$downloaded")
                            }
                        },
                    ) { event ->
                        count.incrementAndGet()
                        bytes.addAndGet(event.content.length.toLong())
                    }
                val wall = System.nanoTime() - start
                report("negentropy sync", count.get(), bytes.get(), wall)
                println(
                    "    relay reported need=${result.needCount} ids for the full set, " +
                        "reconciled in ${result.windows} window(s), downloaded=${result.downloaded}",
                )
            } catch (e: NegentropySyncException) {
                println("  !! negentropy failed: ${e.reason} — ${e.message}")
            } catch (e: Exception) {
                println("  !! negentropy errored: ${e::class.simpleName} ${e.message}")
            } finally {
                client.close()
            }
        }

        // B) Plain until-cursor paging of the same filter, same cap.
        run {
            val client = NostrClient(BasicOkHttpWebSocket.Builder { httpClient })
            try {
                val count = AtomicLong(0)
                val bytes = AtomicLong(0)
                var pages = 0
                val start = System.nanoTime()
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(PROD_KIND), limit = PROD_MAX_EVENTS)),
                    idleTimeoutMs = 30_000L,
                    onNewPage = { pages++ },
                ) { event ->
                    count.incrementAndGet()
                    bytes.addAndGet(event.content.length.toLong())
                }
                val wall = System.nanoTime() - start
                report("paged download", count.get(), bytes.get(), wall)
                println("    pages=${pages + 1}")
            } catch (e: Exception) {
                println("  !! paged download errored: ${e::class.simpleName} ${e.message}")
            } finally {
                client.close()
            }
        }
    }

    // ------------------------------------------------------------------
    // negentropySync pipelining shootout: old sequential behavior
    // (reconcileConcurrency=1, shallow buffer) vs the tuned pipeline, on the
    // same production corpus, both capped at the same number of events. The
    // per-connection by-id ceiling measured by ByIdFetchBenchmark (~5.5k/s on
    // the 2.6M corpus) is the target; the old defaults measured ~1.6k/s.
    // ------------------------------------------------------------------

    private suspend fun negentropyVariant(
        label: String,
        httpClient: OkHttpClient,
        maxEvents: Int,
        maxConcurrentReqs: Int,
        reconcileConcurrency: Int,
        idBufferBatches: Int,
        budgetMs: Long,
    ) {
        val relay = PROD_RELAY.normalizeRelayUrl()
        val client = NostrClient(BasicOkHttpWebSocket.Builder { httpClient })
        val count = AtomicLong(0)
        val start = System.nanoTime()
        try {
            val result =
                withTimeoutOrNull(budgetMs) {
                    client.negentropySync(
                        relay = relay,
                        filter = Filter(kinds = listOf(PROD_KIND)),
                        maxEvents = maxEvents,
                        maxConcurrentReqs = maxConcurrentReqs,
                        reconcileConcurrency = reconcileConcurrency,
                        idBufferBatches = idBufferBatches,
                    ) { count.incrementAndGet() }
                }
            val wall = System.nanoTime() - start
            report(label, count.get(), 0, wall)
            if (result == null) {
                println("    (budget ${budgetMs}ms exceeded — partial)")
            } else {
                println("    windows=${result.windows} need=${result.needCount}")
            }
        } catch (e: NegentropySyncException) {
            val wall = System.nanoTime() - start
            report(label, count.get(), 0, wall)
            println("    !! failed: ${e.reason} ${e.message}")
        } finally {
            client.close()
        }
    }

    @Test
    fun negentropyPipelineShootout() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("negentropyPipelineShootout skipped. Run with -PprodRelayBench=1 to enable.")
            return
        }

        val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

        println("=== NEGENTROPY PIPELINE SHOOTOUT: $PROD_RELAY kinds=[$PROD_KIND], cap 100k events ===")
        runBlocking {
            // Old behavior: sequential windows, shallow id buffer.
            negentropyVariant(
                "baseline (8 reqs, seq windows)",
                httpClient,
                maxEvents = 100_000,
                maxConcurrentReqs = 8,
                reconcileConcurrency = 1,
                idBufferBatches = 8,
                budgetMs = 240_000L,
            )
            // Tuned: 12 + 4 + 1 keep-alive = 17 subs, under strfry's 20 cap.
            negentropyVariant(
                "tuned (12 reqs, 4 reconcilers)",
                httpClient,
                maxEvents = 100_000,
                maxConcurrentReqs = 12,
                reconcileConcurrency = 4,
                idBufferBatches = 96,
                budgetMs = 240_000L,
            )
        }
        httpClient.dispatcher.executorService.shutdown()
    }

    @Test
    fun negentropyFanOutShootout() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("negentropyFanOutShootout skipped. Run with -PprodRelayBench=1 to enable.")
            return
        }

        val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

        val relay = PROD_RELAY.normalizeRelayUrl()
        println("=== NEGENTROPY FAN-OUT SHOOTOUT: $PROD_RELAY kinds=[$PROD_KIND], cap 100k events ===")

        runBlocking {
            // Baseline: one connection, tuned pipeline (measured ~4.5k/s).
            negentropyVariant(
                "single client (12r/4rc)",
                httpClient,
                maxEvents = 100_000,
                maxConcurrentReqs = 12,
                reconcileConcurrency = 4,
                idBufferBatches = 96,
                budgetMs = 240_000L,
            )

            // Fan-out: 4 connections, 10 download REQs each + reconcile on the first.
            val clients = List(4) { NostrClient(BasicOkHttpWebSocket.Builder { httpClient }) }
            try {
                val count = AtomicLong(0)
                val start = System.nanoTime()
                val result =
                    withTimeoutOrNull(240_000L) {
                        negentropySyncFanOut(
                            clients = clients,
                            relay = relay,
                            filter = Filter(kinds = listOf(PROD_KIND)),
                            maxEvents = 100_000,
                            reqsPerClient = 8,
                            fetchBatch = 250,
                            reconcileConcurrency = 6,
                        ) { count.incrementAndGet() }
                    }
                val wall = System.nanoTime() - start
                report("fan-out 4 clients x 8 reqs", count.get(), 0, wall)
                if (result == null) {
                    println("    (budget exceeded — partial)")
                } else {
                    println("    windows=${result.windows} need=${result.needCount} connectionsUsed=${result.connections}")
                }
            } catch (e: Exception) {
                println("  !! fan-out errored: ${e::class.simpleName} ${e.message}")
            } finally {
                clients.forEach { it.close() }
            }
        }
        httpClient.dispatcher.executorService.shutdown()
    }

    // ------------------------------------------------------------------
    // Per-connection wall: is a single connection limited by the relay's
    // pacing or by the client's serial parse? A raw socket that only scans
    // frames (no JSON parse, no quartz stack) pages the same filter on one
    // connection. If it caps at the same events/s as the full quartz stack,
    // the wall is the relay/network; if it runs much faster, the client's
    // per-connection consumer is the wall and parallel parse would help.
    // ------------------------------------------------------------------

    private fun rawPagedDownload(
        relayWsUrl: String,
        httpClient: OkHttpClient,
        kind: Int,
        pageLimit: Int,
        maxEvents: Int,
    ) {
        var count = 0L
        var bytes = 0L
        var pages = 0
        var pageCount = 0
        var pageMinCreatedAt = Long.MAX_VALUE
        var until = Long.MAX_VALUE / 2

        // Cheap created_at scan — the only per-frame work besides counting.
        fun scanCreatedAt(frame: String): Long {
            val i = frame.indexOf("\"created_at\":")
            if (i < 0) return Long.MAX_VALUE
            var j = i + 13
            var v = 0L
            while (j < frame.length && frame[j].isDigit()) {
                v = v * 10 + (frame[j] - '0')
                j++
            }
            return v
        }

        val start = System.nanoTime()
        val latch = CountDownLatch(1)
        val socket =
            httpClient.newWebSocket(
                Request.Builder().url(relayWsUrl).build(),
                object : okhttp3.WebSocketListener() {
                    override fun onOpen(
                        webSocket: okhttp3.WebSocket,
                        response: Response,
                    ) {
                        // Compression matters 3-5x on bandwidth-limited links; report
                        // whether the relay actually negotiated it with OkHttp.
                        println("    negotiated Sec-WebSocket-Extensions: ${response.header("Sec-WebSocket-Extensions") ?: "(none — uncompressed)"}")
                        webSocket.send("""["REQ","raw",{"kinds":[$kind],"limit":$pageLimit,"until":$until}]""")
                    }

                    override fun onMessage(
                        webSocket: okhttp3.WebSocket,
                        text: String,
                    ) {
                        if (text.startsWith("[\"EVENT\"")) {
                            count++
                            bytes += text.length
                            pageCount++
                            val ts = scanCreatedAt(text)
                            if (ts < pageMinCreatedAt) pageMinCreatedAt = ts
                        } else if (text.startsWith("[\"EOSE\"")) {
                            pages++
                            val done = pageCount == 0 || count >= maxEvents || pageMinCreatedAt == Long.MAX_VALUE
                            if (done) {
                                latch.countDown()
                            } else {
                                until = pageMinCreatedAt - 1
                                pageCount = 0
                                pageMinCreatedAt = Long.MAX_VALUE
                                webSocket.send("""["REQ","raw",{"kinds":[$kind],"limit":$pageLimit,"until":$until}]""")
                            }
                        }
                    }

                    override fun onFailure(
                        webSocket: okhttp3.WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        println("  !! raw socket failure: ${t.message}")
                        latch.countDown()
                    }
                },
            )

        latch.await(120, TimeUnit.SECONDS)
        val wall = System.nanoTime() - start
        socket.cancel()
        report("raw paged (no parse), 1 conn", count, bytes, wall)
        println("    pages=$pages")
    }

    @Test
    fun perConnectionWall() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("perConnectionWall skipped. Run with -PprodRelayBench=1 to enable.")
            return
        }

        val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

        println("\n=== PER-CONNECTION WALL: $PROD_RELAY kinds=[$PROD_KIND], 1 connection, cap $PROD_MAX_EVENTS ===")

        // A) raw socket, until-cursor paging, no JSON parse at all
        rawPagedDownload(PROD_RELAY, httpClient, PROD_KIND, pageLimit = 500, maxEvents = PROD_MAX_EVENTS)

        // B) full quartz stack, same query, same connection count
        runBlocking {
            val relay = PROD_RELAY.normalizeRelayUrl()
            val client = NostrClient(BasicOkHttpWebSocket.Builder { httpClient })
            try {
                val count = AtomicLong(0)
                val bytes = AtomicLong(0)
                var pages = 0
                val start = System.nanoTime()
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(PROD_KIND), limit = PROD_MAX_EVENTS)),
                    idleTimeoutMs = 30_000L,
                    onNewPage = { pages++ },
                ) { event ->
                    count.incrementAndGet()
                    bytes.addAndGet(event.content.length.toLong())
                }
                report("quartz paged (full parse), 1 conn", count.get(), bytes.get(), System.nanoTime() - start)
                println("    pages=${pages + 1}")
            } finally {
                client.close()
            }
        }

        httpClient.dispatcher.executorService.shutdown()
    }

    // ------------------------------------------------------------------

    @Test
    fun bulkDownloadBenchmark() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("BulkDownloadBenchmark skipped. Run with -PprodRelayBench=1 to enable.")
            return
        }

        val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

        println("=== BULK DOWNLOAD BENCHMARK === cores=${Runtime.getRuntime().availableProcessors()}")

        localCeilings(httpClient)
        offlineParseStrategies()
        runBlocking { productionNegentropy(httpClient) }

        httpClient.dispatcher.executorService.shutdown()
    }
}
