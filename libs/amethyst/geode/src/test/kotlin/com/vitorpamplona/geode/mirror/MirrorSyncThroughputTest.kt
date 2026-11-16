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
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySession
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Measures geode's sync throughput (events/second) pulling a large set
 * into an empty downstream geode over the real WebSocket transport.
 *
 * Source selection:
 *  - default (`-DsyncSourceUrl` unset): an **in-process geode** upstream
 *    ([KtorRelay]) preloaded with N events, mirrored via the production
 *    [MirrorWorker] — the **geode→geode** number.
 *  - `-DsyncSourceUrl=ws://host:port`: pull from that external relay (e.g. a
 *    strfry loaded with the corpus) — the **strfry→geode** number.
 *    `-DsyncExpect=N` sets the convergence target.
 *
 * Why the external path does NOT use [MirrorWorker]: the worker funnels the
 * upstream's EVENTs through an **unbounded** intake channel (its listener
 * callback can't suspend, so it `trySend`s and never blocks the shared OkHttp
 * reader — see the MirrorWorker kdoc). That is the right trade for live
 * tailing, but a 1M **bulk backfill** from a fast relay overruns it: strfry
 * serves ~78k ev/s while geode ingests ~10–15k ev/s, so the unbounded channel
 * balloons past the heap and the JVM OOMs mid-sync (observed: the mirror
 * consumer dies at ~16k/1M). To measure geode's real ingest rate from strfry
 * we drive [NostrServer.ingest] directly from a **dedicated** socket whose
 * reader we are free to block: a bounded hand-off channel applies TCP
 * backpressure right back to strfry, so the sink pulls at exactly the rate it
 * can persist. (The MirrorWorker's unbounded-intake fragility under bulk
 * backfill is a separate finding — see
 * relayBench/plans/2026-07-04-sync-throughput-1m.md.)
 *
 * Size with `-DsyncN` (default 1,000,000). Timed from first byte to the
 * downstream reaching the target (or plateauing).
 *
 * **Opt-in.** This is a benchmark, not a regression test: it preloads a
 * million events and pulls them over a real WebSocket, which took 4,584 s of
 * `:geode:test`'s 4,636 s total — 98.9% of the module's test time for one test
 * that asserts nothing about correctness. Every other benchmark in this module
 * is already gated the same way (see `perf.LoadBenchmark`), and every
 * invocation documented above passes `-DsyncN` or `-DsyncSourceUrl`, so those
 * still run it. A plain `./gradlew test` — which is what the pre-push hook
 * runs — now skips it in milliseconds.
 */
class MirrorSyncThroughputTest {
    // geode's real relay config (deferred FTS, live negentropy index) — not the
    // default EventStore(null), whose synchronous FTS tokenization on every
    // insert would dominate ingest and misrepresent the sync rate.
    // `-DsyncLiveIndex=false` disables the live negentropy index to isolate its
    // O(n)-insert cost during out-of-order backfill.
    private val liveIndex = System.getProperty("syncLiveIndex")?.toBoolean() ?: true

    // NIP-50 full-text search (geode's default; strfry has none) — pass
    // `-DsyncFts=false` to match strfry for an apples-to-apples sync.
    private val fts = System.getProperty("syncFts")?.toBoolean() ?: true

    // Schnorr verification on the sink's ingest. `strfry sync` always verifies
    // received events, so the negentropy sink defaults to verifying too; pass
    // `-DsyncVerify=false` for the trusted-mirror (skip-verify) rate.
    private val verifySink = System.getProperty("syncVerify")?.toBoolean() ?: true
    private val strategy =
        DefaultIndexingStrategy(
            indexEventsByCreatedAtAlone = true,
            indexEventsByPubkeyAlone = true,
            indexFullTextSearch = fts,
            deferFullTextSearchIndexing = true,
            maintainLiveNegentropyIndex = liveIndex,
        )
    private val downstreamStore = EventStore(dbName = null, indexStrategy = strategy)
    private val downstream =
        RelayEngine(url = "ws://127.0.0.1:7899/".normalizeRelayUrl(), store = downstreamStore, parallelVerify = true)

    private var upstreamStore: EventStore? = null
    private var upstream: RelayEngine? = null
    private var server: KtorRelay? = null
    private var worker: MirrorWorker? = null

    @AfterTest
    fun tearDown() {
        worker?.close()
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
        upstream?.close()
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

    /**
     * True when someone actually asked for a throughput number: either the
     * module-wide benchmark switch, or any of this test's own sizing/source
     * properties. Naming a size IS the opt-in — a run that says `-DsyncN=…`
     * plainly wants the measurement and should not need a second flag.
     */
    private val enabled =
        System.getProperty("runLoadBenchmark") == "true" ||
            System.getProperty("syncN") != null ||
            System.getProperty("syncSourceUrl") != null

    @Test
    fun mirrorSyncThroughput() =
        runBlocking {
            // Bail before building anything. The old code decided nothing up
            // front and spent over an hour preloading and syncing a million
            // events on every ordinary test run.
            if (!enabled) {
                println(
                    "[skip] mirrorSyncThroughput — benchmark. Enable with -DrunLoadBenchmark=true, " +
                        "or size it directly with -DsyncN=… / -DsyncSourceUrl=…",
                )
                return@runBlocking
            }
            val n = System.getProperty("syncN")?.toInt() ?: 1_000_000
            val externalUrl = System.getProperty("syncSourceUrl")
            val expect = System.getProperty("syncExpect")?.toInt() ?: n
            // The backfill window (seconds before now). The synthetic in-process
            // source stamps events within the last few hours, so the 25h default
            // covers it; a real corpus (e.g. strfry's damus.io 1M) spans days, so
            // `-DsyncBackfillSeconds` must reach past its oldest event or the sync
            // only pulls the recent slice.
            val backfillSeconds = System.getProperty("syncBackfillSeconds")?.toLong() ?: 90_000L

            if (externalUrl != null) {
                // Default to NIP-77 negentropy (client-paced, completes a bulk
                // pull from strfry); `-DsyncMode=req` runs the paged-REQ drain
                // that stalls when strfry kills a slow client (see class kdoc).
                if ((System.getProperty("syncMode") ?: "negentropy") == "req") {
                    measureExternalDrain(externalUrl, expect, backfillSeconds)
                } else {
                    measureNegentropyDrain(externalUrl, expect)
                }
                return@runBlocking
            }

            // ── geode→geode: in-process source preloaded with N events, mirrored
            // via the production MirrorWorker. Source is infrastructure (serves
            // the REQ); keep its preload fast by not maintaining the live index.
            val srcStrategy =
                DefaultIndexingStrategy(
                    indexEventsByCreatedAtAlone = true,
                    indexEventsByPubkeyAlone = true,
                    indexFullTextSearch = true,
                    deferFullTextSearchIndexing = true,
                    maintainLiveNegentropyIndex = false,
                )
            val store = EventStore(dbName = null, indexStrategy = srcStrategy).also { upstreamStore = it }
            val now = TimeUtils.now()
            val sig = "f".repeat(128)
            val batch = ArrayList<Event>(10_000)
            var loaded = 0
            for (i in 0 until n) {
                batch.add(
                    Event(
                        id = hex64(7, i),
                        pubKey = hex64(3, i % 20_000),
                        createdAt = now - 60 - (i % 20_000),
                        kind = 1,
                        tags = emptyArray(),
                        content = "e$i",
                        sig = sig,
                    ),
                )
                if (batch.size == 10_000) {
                    store.batchInsert(batch)
                    loaded += batch.size
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) store.batchInsert(batch)
            val eng = RelayEngine(url = "ws://127.0.0.1:7898/".normalizeRelayUrl(), store = store).also { upstream = it }
            val srv = KtorRelay(eng, host = "127.0.0.1", port = 0).start().also { server = it }
            val port =
                srv.url
                    .normalizeRelayUrl()
                    .url
                    .substringAfterLast(':')
                    .trimEnd('/')
                    .toInt()
            val sourceUrl = "ws://127.0.0.1:$port/"
            println("─ MirrorSyncThroughput: geode→geode, in-process source preloaded $loaded on $port ─")

            val startNanos = System.nanoTime()
            worker =
                MirrorWorker(
                    upstreams =
                        listOf(
                            MirrorUpstream(
                                url = sourceUrl.normalizeRelayUrl(),
                                trusted = true,
                                backfillSeconds = backfillSeconds,
                            ),
                        ),
                    server = downstream.server,
                ).also { it.start() }

            val reached = awaitConvergence(expect, startNanos)
            report("geode", reached, expect, startNanos)
        }

    /**
     * strfry→geode: pull the corpus off an external relay with a plain REQ and
     * ingest it under backpressure. A dedicated OkHttp socket delivers EVENTs on
     * its reader thread; each is handed to a bounded channel via
     * [trySendBlocking], which BLOCKS that reader when the channel is full. That
     * stalls our socket reads, TCP backpressure throttles the upstream, and the
     * sink pulls at exactly its persist rate — no unbounded buffering, no OOM.
     */
    private suspend fun measureExternalDrain(
        url: String,
        expect: Int,
        backfillSeconds: Long,
    ) = coroutineScope {
        val since = TimeUtils.now() - backfillSeconds
        println("─ MirrorSyncThroughput: strfry→geode, paged drain of EXTERNAL $url since $since, expect $expect ─")

        // Bounded hand-off: reader thread → ingest coroutine. The bounded
        // IngestQueue (cap 1024) behind server.ingest is the real limiter; this
        // just decouples parse from persist without unbounded buffering.
        val handoff = Channel<Event>(capacity = 8192)
        val consumer =
            launch {
                for (ev in handoff) {
                    downstream.server.ingest(ev, skipVerify = !verifySink) { }
                }
            }

        // Per-page state, written on the OkHttp reader thread, read on the
        // pager coroutine → atomics for visibility. `pageMin` tracks the oldest
        // created_at seen this page (the next page's `until` cursor); `pageCount`
        // is how many the page returned (a short page = the tail).
        val pageMin = AtomicLong(Long.MAX_VALUE)
        val pageCount = AtomicInteger(0)
        val pageDone = Channel<Unit>(Channel.CONFLATED)

        val http = if (url.startsWith("ws://")) "http://" + url.removePrefix("ws://") else url
        val okhttp = OkHttpClient.Builder().build()
        val startNanos = System.nanoTime()
        val listener =
            object : WebSocketListener() {
                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    when {
                        text.startsWith("[\"EVENT\"") -> {
                            val ev =
                                runCatching { (OptimizedJsonMapper.fromJsonToMessage(text) as? EventMessage)?.event }
                                    .getOrNull() ?: return
                            if (ev.createdAt < pageMin.get()) pageMin.set(ev.createdAt)
                            pageCount.incrementAndGet()
                            // Blocks THIS dedicated reader when the sink falls
                            // behind — TCP backpressure the upstream honours only
                            // because each page is bounded (see below).
                            handoff.trySendBlocking(ev)
                        }
                        text.startsWith("[\"EOSE\"") -> pageDone.trySend(Unit)
                    }
                }
            }
        val ws = okhttp.newWebSocket(Request.Builder().url(http).build(), listener)

        // Client-paced paging. strfry's REQ push isn't flow-controlled: an
        // unbounded REQ dumps the whole scan into strfry's outbound buffer and
        // it kills (or, with the cap lifted, OOMs) a slower client. So we walk
        // the corpus newest→oldest in bounded `limit` windows, sending the next
        // window's REQ only after the current one's EOSE.
        //
        // NOTE: this reduces but does NOT eliminate the overrun — a single
        // page still exceeds strfry's 32 MB `maxPendingOutboundBytes` when our
        // reader backpressures long enough, and strfry then kills the conn. On
        // the 1M damus.io corpus that happens ~310k events in (geode's ingest
        // decays as its in-memory store grows). A REQ pull from strfry
        // therefore does not complete at 1M; NIP-77 negentropy is the only
        // mechanism that does (see plans/2026-07-04-sync-throughput-1m.md).
        val pageLimit = 20_000
        val pager =
            launch {
                var until = TimeUtils.now() + 1
                var k = 0
                while (isActive) {
                    pageMin.set(Long.MAX_VALUE)
                    pageCount.set(0)
                    val subId = "drain-${k++}"
                    ws.send("""["REQ","$subId",{"until":$until,"since":$since,"limit":$pageLimit}]""")
                    pageDone.receive()
                    ws.send("""["CLOSE","$subId"]""")
                    val got = pageCount.get()
                    if (got < pageLimit) break // tail page
                    val next = pageMin.get()
                    if (next >= until) break // all one timestamp — can't advance
                    until = next // overlap by =until re-sends the boundary ts (dupes rejected)
                    if (downstreamStore.count(Filter()) >= expect) break
                }
            }

        val reached = awaitConvergence(expect, startNanos)

        pager.cancel()
        ws.cancel()
        handoff.close()
        consumer.join()
        okhttp.dispatcher.executorService.shutdown()
        report("strfry", reached, expect, startNanos)
    }

    /**
     * strfry→geode over **NIP-77 negentropy** — the same mechanism `strfry
     * sync` uses, and the only one that completes a bulk pull from strfry
     * (client-paced, so strfry can't kill us for being slow).
     *
     *  1. **Reconcile**: our local set is empty, so a NEG-OPEN → NEG-MSG*
     *     negotiation over `Filter()` (the whole DB) returns *every* strfry id
     *     as "need". Frames are bounded (`frameSizeLimit`) so neither side ever
     *     ships a multi-MB payload.
     *  2. **Fetch + ingest**: REQ the need-ids in bounded batches, sending the
     *     next batch only after the current one's EOSE, and hand each event to
     *     the (backpressured) ingest queue. strfry never buffers more than one
     *     batch, so the 32 MB pending wall the REQ drain hits never comes.
     */
    private suspend fun measureNegentropyDrain(
        url: String,
        expect: Int,
    ) = coroutineScope {
        println("─ MirrorSyncThroughput: strfry→geode, NIP-77 negentropy sync of $url, expect $expect ─")
        val http = if (url.startsWith("ws://")) "http://" + url.removePrefix("ws://") else url
        val okhttp = OkHttpClient.Builder().build()
        val incoming = Channel<String>(Channel.UNLIMITED)
        val listener =
            object : WebSocketListener() {
                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    incoming.trySend(text)
                }
            }
        val ws = okhttp.newWebSocket(Request.Builder().url(http).build(), listener)
        val startNanos = System.nanoTime()

        // 1. Reconcile: empty local set → need = strfry's entire id set.
        val session = NegentropySession.fromEvents("gsync", Filter(), emptyList(), frameSizeLimit = 60_000L)
        val needIds = LinkedHashSet<String>()
        ws.send(OptimizedJsonMapper.toJson(session.open()))
        var rounds = 0
        while (rounds < 20_000) {
            val raw = withTimeout(180_000) { incoming.receive() }
            // Skip anything that isn't a negotiation frame.
            if (!raw.startsWith("[\"NEG-MSG\"") && !raw.startsWith("[\"NEG-ERR\"") && !raw.startsWith("[\"NOTICE\"")) continue
            rounds++
            when (val msg = OptimizedJsonMapper.fromJsonToMessage(raw)) {
                is NegErrMessage -> error("NEG-ERR from strfry: ${msg.reason}")
                is NoticeMessage -> continue
                is NegMsgMessage -> {
                    val r = session.processMessage(msg.message)
                    needIds += r.needIds
                    if (r.isComplete()) {
                        ws.send("""["NEG-CLOSE","gsync"]""")
                        break
                    }
                    ws.send(OptimizedJsonMapper.toJson(r.nextCmd!!))
                }
                else -> {}
            }
        }
        val reconcileMs = (System.nanoTime() - startNanos) / 1e6
        println("    reconcile: need=${needIds.size} in $rounds rounds, %.0f ms".format(reconcileMs))

        // 2. Fetch the need-ids in bounded batches; ingest under backpressure.
        var lastLog = System.nanoTime()
        var lastCount = 0
        for ((bi, batch) in needIds.chunked(2_000).withIndex()) {
            val subId = "gfetch-$bi"
            ws.send("""["REQ","$subId",${Filter(ids = batch).toJson()}]""")
            while (true) {
                val raw = withTimeout(180_000) { incoming.receive() }
                if (raw.startsWith("[\"EVENT\",\"$subId\"")) {
                    val ev = (OptimizedJsonMapper.fromJsonToMessage(raw) as? EventMessage)?.event ?: continue
                    downstream.server.ingest(ev, skipVerify = !verifySink) { }
                } else if (raw.startsWith("[\"EOSE\",\"$subId\"") || raw.startsWith("[\"CLOSED\",\"$subId\"")) {
                    ws.send("""["CLOSE","$subId"]""")
                    break
                }
            }
            val nowNanos = System.nanoTime()
            if (nowNanos - lastLog >= 3_000_000_000L) {
                val c = downstreamStore.count(Filter())
                val rate = ((c - lastCount) / ((nowNanos - lastLog) / 1e9)).toLong()
                println("    …ingested $c/${needIds.size}  (%,d ev/s inst)".format(rate))
                lastLog = nowNanos
                lastCount = c
            }
        }

        // Let the async ingest queue drain to the store.
        var reached = downstreamStore.count(Filter())
        var stable = 0
        while (reached < expect && stable < 20) {
            delay(250)
            val c = downstreamStore.count(Filter())
            if (c == reached) {
                stable++
            } else {
                stable = 0
                reached = c
            }
        }

        ws.cancel()
        okhttp.dispatcher.executorService.shutdown()
        report("strfry", reached, expect, startNanos)
    }

    /** Poll the downstream count until it reaches [expect] or plateaus. */
    private suspend fun awaitConvergence(
        expect: Int,
        startNanos: Long,
    ): Int {
        var reached = 0
        var last = -1
        var stable = 0
        var lastLog = startNanos
        var lastLogCount = 0
        withTimeoutOrNull(600_000) {
            while (true) {
                val c = downstreamStore.count(Filter())
                reached = c
                if (c >= expect) break
                val nowNanos = System.nanoTime()
                if (nowNanos - lastLog >= 3_000_000_000L) {
                    val instRate = ((c - lastLogCount) / ((nowNanos - lastLog) / 1e9)).toLong()
                    println("    …$c/$expect  (%,d ev/s inst)".format(instRate))
                    lastLog = nowNanos
                    lastLogCount = c
                }
                if (c == last) {
                    if (++stable >= 30) break // ~15s no progress
                } else {
                    stable = 0
                    last = c
                }
                delay(500)
            }
        }
        return reached
    }

    private fun report(
        src: String,
        reached: Int,
        expect: Int,
        startNanos: Long,
    ) {
        val secs = (System.nanoTime() - startNanos) / 1e9
        val eps = (reached / secs).toLong()
        println("════════════════════════════════════════════")
        println("$src→geode: synced $reached/$expect in %.1fs  =>  %,d events/s".format(secs, eps))
        println("════════════════════════════════════════════")
    }
}
