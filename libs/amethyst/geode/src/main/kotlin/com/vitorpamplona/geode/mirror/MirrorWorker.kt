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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropySyncException
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.SyncCoverage
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcile
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySync
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.TcpNoDelaySocketFactory
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Which way events flow between this relay and one upstream —
 * strfry-router's per-stream `dir`.
 */
enum class MirrorDirection {
    /** Pull: subscribe to the upstream and ingest what it sends. */
    DOWN,

    /** Push: publish this relay's matching events to the upstream. */
    UP,

    /** Pull and push. Echo suppression keeps the two from ping-ponging. */
    BOTH,
    ;

    companion object {
        /** Parses strfry's `"down"` / `"up"` / `"both"`; null if unknown. */
        fun parse(value: String): MirrorDirection? =
            when (value.lowercase()) {
                "down" -> DOWN
                "up" -> UP
                "both" -> BOTH
                else -> null
            }
    }
}

/**
 * One upstream relay this relay mirrors, from the `[[mirror]]` config.
 *
 * [trusted] is the relay-to-relay trust switch: events streamed from this
 * upstream skip Schnorr signature verification on ingest. The trusted
 * identity is [url] — the address *this* relay dialed (TLS-authenticated
 * for `wss://`), never anything the peer claims — so the skip can't be
 * hijacked by an inbound client. Only meaningful for [MirrorDirection.DOWN]
 * / [MirrorDirection.BOTH]; the up direction never verifies (the upstream
 * does its own gatekeeping).
 */
class MirrorUpstream(
    val url: NormalizedRelayUrl,
    val trusted: Boolean,
    /** How far back the initial replay reaches, in BOTH directions. 0 = live-only from connect. */
    val backfillSeconds: Long = 0L,
    /**
     * Optional scope for this upstream (strfry-router's per-stream
     * `filter`). Applied symmetrically: down, it shapes the REQ sent
     * upstream AND every delivered event is re-checked before ingest —
     * so even a [trusted] upstream can only inject events inside the
     * declared scope; up, it selects which local events are pushed. Its
     * `since`/`limit` are ignored ([backfillSeconds] owns the time
     * window; the subscriptions are unbounded). `null` mirrors
     * everything.
     */
    val filter: Filter? = null,
    /** Flow direction — strfry-router's `dir`. Defaults to pull-only. */
    val direction: MirrorDirection = MirrorDirection.DOWN,
)

/**
 * Streams events from configured upstream relays into the local relay —
 * geode's equivalent of `strfry router` in the "down" direction.
 *
 * One [NostrClient] holds every upstream connection; the client owns
 * reconnects, exponential backoff, and re-sending the REQ after a drop.
 * Each upstream gets its own subscription over an open filter
 * (`since = now - backfill`), and every EVENT that arrives is handed to
 * [NostrServer.ingest] — the same group-commit writer and live fanout a
 * client publish takes — with `skipVerify` set for [MirrorUpstream.trusted]
 * upstreams (the upstream already verified its ingest; re-verifying here
 * only burns CPU — Schnorr verify profiles at ~8% of busy ingest CPU).
 *
 * After a reconnect the upstream replays everything since the boot-time
 * `since`; replayed duplicates are rejected by the store's unique id
 * constraint and only show up in [rejected].
 *
 * Listener callbacks can't suspend, so events funnel through an unbounded
 * [inbound] channel into one consumer coroutine whose [NostrServer.ingest]
 * call suspends on the ingest queue's backpressure. The buffer is unbounded
 * for the same reason the client's receive channels are (see
 * `BasicOkHttpWebSocket`): blocking the socket reader parks the backlog on
 * infrastructure that isn't ours.
 */
class MirrorWorker(
    private val upstreams: List<MirrorUpstream>,
    private val server: NostrServer,
    /**
     * The local event set — read (never written) to enumerate the ids we
     * already hold so the negentropy catch-up ([MirrorUpstream.backfillSeconds]
     * > 0) reconciles against them and downloads only the diff, exactly like
     * `strfry sync`. `null` reconciles against an empty local set (a full
     * re-download of the window, which the store's unique-id constraint dedups)
     * — used by tests that don't wire a store.
     */
    private val store: IEventStore? = null,
    /**
     * Transport override for tests (e.g. `InProcessRelays`). Defaults to
     * a real OkHttp WebSocket per upstream.
     */
    websocketBuilder: WebsocketBuilder? = null,
    /**
     * Whether the down catch-up uses NIP-77 negentropy (`strfry sync`) for the
     * historical window before the live REQ tail takes over. The geode binary
     * turns this on (see `Main`); it defaults **off** so the many existing
     * MirrorWorker tests keep exercising the pure live-REQ path unchanged.
     * When on, the catch-up automatically falls back to paged REQ against an
     * upstream that doesn't speak NIP-77 — so "either mode" is transparent
     * and needs no separate toggle.
     */
    private val negentropyBackfill: Boolean = false,
    /**
     * Resume memory for the down catch-up, shared across upstreams and — via
     * [SyncCoverageFile] — across restarts. Null keeps the old behavior:
     * every boot re-syncs the whole backfill window, which for an upstream
     * without NIP-77 is a full re-download.
     */
    private val coverage: SyncCoverage? = null,
) : AutoCloseable {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * The ping interval matters on a long-running daemon: a half-open
     * upstream connection (network drop with no FIN) never fires
     * onDisconnected on its own, so without pings the client would
     * believe it is connected forever and stop mirroring silently. A
     * missed pong fails the socket, which routes into the normal
     * disconnect → backoff → re-dial path. Same value the Android app
     * uses for its relay pool.
     */
    private val okhttp: OkHttpClient? =
        if (websocketBuilder == null) {
            OkHttpClient
                .Builder()
                .socketFactory(TcpNoDelaySocketFactory)
                .pingInterval(Duration.ofSeconds(PING_INTERVAL_SECS))
                .build()
        } else {
            null
        }

    private val client =
        NostrClient(
            websocketBuilder = websocketBuilder ?: BasicOkHttpWebSocket.Builder { okhttp!! },
            parentScope = scope,
        )

    private class Inbound(
        val event: Event,
        val skipVerify: Boolean,
    )

    private val inbound = Channel<Inbound>(Channel.UNLIMITED)

    /** Events accepted into the local store (excludes duplicates). */
    val accepted = AtomicLong(0)

    /** Events the store rejected — mostly duplicate replays after a reconnect. */
    val rejected = AtomicLong(0)

    /**
     * Good events the store could not write ([IEventStore.InsertOutcome.Failed]).
     * The store's fault, not the event's, and nothing re-offers them — counted
     * apart from [rejected] so a schema drift reads as store damage rather
     * than as upstreams sending junk.
     */
    val failed = AtomicLong(0)

    /**
     * Deliveries dropped before ever reaching the store: events outside
     * the [MirrorUpstream.filter] scope (an upstream answering outside
     * the REQ it was given) and events delivered by a different relay
     * than the one the subscription dialed (a peer answering with a
     * subscription id that isn't its own).
     */
    val filtered = AtomicLong(0)

    /** Local events handed to the client's outbox for an up-direction upstream. */
    val sentUp = AtomicLong(0)

    /**
     * How many times a down subscription advanced its `since` watermark
     * on a reconnect (test/observability hook). Each advance is one
     * avoided full-window replay.
     */
    val sinceAdvances = AtomicLong(0)

    /**
     * One down subscription's re-subscribe state. The upstream replays
     * everything at or after the REQ's `since` on every (re)connect; left
     * at the boot-time value, a month-old daemon re-streams a month of
     * events on every flap. So we track the newest `created_at` ingested
     * from this upstream and, on a disconnect, advance the REQ's `since`
     * to `watermark - overlap` before the reconnect re-sends it. The
     * overlap re-requests a small tail (dup-safe against the store's
     * unique-id constraint) to cover out-of-order streaming and clock
     * skew. Advancing only on disconnect keeps a healthy connection from
     * ever re-querying — no steady-state cost.
     */
    private inner class DownSub(
        val subId: String,
        val up: MirrorUpstream,
        val scopedBase: Filter,
        val listener: SubscriptionListener,
        initialSince: Long,
        val watermark: AtomicLong,
    ) {
        @Volatile
        var issuedSince: Long = initialSince

        fun advanceSinceOnReconnect() {
            val candidate = watermark.get() - WATERMARK_OVERLAP_SECS
            if (candidate > issuedSince) {
                issuedSince = candidate
                client.subscribe(
                    subId = subId,
                    filters = mapOf(up.url to listOf(scopedBase.copy(since = candidate))),
                    listener = listener,
                )
                sinceAdvances.incrementAndGet()
            }
        }
    }

    private val downSubs = mutableListOf<DownSub>()

    /**
     * Recently exchanged event ids, one set per up-capable upstream —
     * the echo suppressor for [MirrorDirection.BOTH]. An event pulled
     * DOWN from an upstream must not be pushed straight back UP to it
     * (and one we pushed up must not be re-ingested when the upstream
     * fans it back on our down subscription). Bounded LRU: eviction only
     * costs a wasted round trip that the stores' unique-id constraints
     * absorb, so correctness never depends on it.
     */
    private class RecentIds(
        private val capacity: Int,
    ) {
        private val map =
            object : LinkedHashMap<String, Boolean>(capacity, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>) = size > capacity
            }

        @Synchronized
        fun add(id: String) {
            map[id] = true
        }

        @Synchronized
        fun contains(id: String): Boolean = map.containsKey(id)
    }

    /** Open in-process sessions feeding the up direction; closed with the worker. */
    private val upSessions = mutableListOf<AutoCloseable>()

    /** Dials every upstream and starts streaming. Call once. */
    fun start() {
        scope.launch {
            for (msg in inbound) {
                try {
                    server.ingest(msg.event, msg.skipVerify) { outcome ->
                        when (outcome) {
                            IEventStore.InsertOutcome.Accepted -> accepted.incrementAndGet()
                            is IEventStore.InsertOutcome.Rejected -> {
                                rejected.incrementAndGet()
                                Log.d("MirrorWorker") { "rejected ${msg.event.id}: ${outcome.reason}" }
                            }
                            is IEventStore.InsertOutcome.Failed -> {
                                // The store's error, not the event's: the event
                                // was good and nothing will re-offer it. Louder
                                // than a rejection on purpose.
                                failed.incrementAndGet()
                                Log.w("MirrorWorker") { "store failed ${msg.event.id}: ${outcome.reason}" }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // A server shutting down closes its ingest queue while
                    // events may still be buffered here; an uncaught throw
                    // would leak into the scope's default handler (and
                    // poison unrelated runTest tests on CI). Stop pulling —
                    // the relay beneath us is going away.
                    Log.w("MirrorWorker") { "ingest failed, stopping mirror consumer: ${e.message}" }
                    break
                }
            }
        }

        val since = TimeUtils.now()
        upstreams.forEachIndexed { i, up ->
            // Echo suppression only matters when events can flow both
            // ways on the same upstream.
            val exchanged = if (up.direction == MirrorDirection.BOTH) RecentIds(EXCHANGED_IDS_CAPACITY) else null

            // The operator's filter scopes the subscriptions in both
            // directions; the mirror owns the time window (since) and
            // never bounds the result (limit). scopedBase carries no
            // since — the down path applies (and later advances) it via
            // the watermark; the up path uses the fixed initial value.
            val scopedBase = (up.filter ?: Filter()).copy(since = null, limit = null)
            val initialSince = since - up.backfillSeconds

            // strfry's two-phase model, both directions (`strfry sync --dir
            // both` + the live router): a one-shot NIP-77 "sync" closes the
            // historical [initialSince, now] gap — down pulls what the upstream
            // has and we lack, up pushes what we have and it lacks — then the
            // live REQ subscription/session tails everything new. When a
            // direction's catch-up is on, that direction's live window starts at
            // `now` (history is the sync's job); the windows overlap at `now` and
            // the store's/upstream's unique-id dedup absorbs the seam.
            val catchUpDown = negentropyBackfill && up.direction != MirrorDirection.UP && up.backfillSeconds > 0
            val catchUpUp = negentropyBackfill && up.direction != MirrorDirection.DOWN && up.backfillSeconds > 0
            val downLiveSince = if (catchUpDown) since else initialSince
            val upLiveSince = if (catchUpUp) since else initialSince

            if (up.direction != MirrorDirection.UP) {
                downSubs += startDown(i, up, scopedBase, downLiveSince, exchanged)
            }
            if (up.direction != MirrorDirection.DOWN) {
                startUp(up, scopedBase.copy(since = upLiveSince), exchanged)
            }
            if (catchUpDown) {
                scope.launch { runCatchUpDown(up, scopedBase, initialSince, since) }
            }
            if (catchUpUp) {
                scope.launch { runCatchUpUp(up, scopedBase, initialSince, since, exchanged) }
            }
        }
        client.connect()

        // Watermark advance: when an upstream drops, bump its REQ's since
        // to the newest event we ingested from it (minus an overlap) so
        // the reconnect doesn't replay the whole window since boot. Only
        // fires on the connected→disconnected edge, so a stable link
        // never re-queries.
        scope.launch {
            var prev = emptySet<NormalizedRelayUrl>()
            client.connectedRelaysFlow().collect { current ->
                val dropped = prev - current
                if (dropped.isNotEmpty()) {
                    downSubs.forEach { if (it.up.url in dropped) it.advanceSinceOnReconnect() }
                }
                prev = current
            }
        }

        // Retry pump. NostrClient re-dials once on disconnect and then
        // relies on its 60s keep-alive — measured as a 61s mirror blackout
        // when an upstream restarts and the immediate re-dial races the
        // port rebind. Poking more often costs nothing: reconnectIfNeedsTo
        // skips connected relays, and each relay's exponential backoff
        // (1s doubling, 5min cap) still gates actual dial attempts, so a
        // long-dead upstream is not hammered — only a briefly-restarting
        // one is picked back up in seconds instead of a minute.
        scope.launch {
            while (true) {
                delay(RECONNECT_POKE_MS)
                client.reconnect(onlyIfChanged = true, ignoreRetryDelays = false)
            }
        }
    }

    /**
     * The NIP-77 "sync" phase — geode's equivalent of `strfry sync --dir down`,
     * run once per down/both upstream to close the historical
     * `[initialSince, until]` gap before (and alongside) the live REQ tail.
     *
     * Reconciles the local set against the upstream and downloads only the diff,
     * **client-paced** so a fast upstream can't overrun the sink: a plain REQ
     * backfill of a large set dies here (strfry kills a slow REQ client once its
     * unsent-outbound buffer crosses `maxPendingOutboundBytes`), which is exactly
     * why this uses negentropy. When the upstream doesn't speak NIP-77 the
     * catch-up falls back to paged REQ, so the mirror is compatible with
     * either kind of upstream with no config.
     *
     * A failure here is non-fatal: the live subscription keeps the mirror current
     * and the reconnect watermark narrows any residual gap.
     */
    private suspend fun runCatchUpDown(
        up: MirrorUpstream,
        scopedBase: Filter,
        initialSince: Long,
        until: Long,
    ) {
        // Resume memory: coverage is keyed on the STABLE scoped filter, never
        // on the boot window — whose since/until change every start and would
        // never match a stored band. The window itself rides along as the
        // floor argument, so a band recorded against a shallower window
        // re-opens the older span when the operator deepens the backfill.
        val legs =
            coverage
                ?.legs(up.url, scopedBase, initialSince)
                ?.mapNotNull { clampToWindow(it, initialSince, until) }
                ?: listOf(scopedBase.copy(since = initialSince, until = until))
        if (legs.isEmpty()) {
            Log.i("MirrorWorker") { "catch-up from ${up.url.url}: window already covered - nothing outside the synced band" }
            return
        }

        // Bounded hand-off → one ingest consumer. `onEvent` can't suspend, so it
        // blocks here when the sink falls behind; because negentropySync's own
        // delivery pipeline is bounded (and a paged REQ is paced by its pages),
        // that backpressure reaches all the way to the upstream — no unbounded
        // buffering (unlike the live-tail path).
        val handoff = Channel<Event>(capacity = CATCHUP_HANDOFF)
        val consumer =
            scope.launch {
                for (event in handoff) {
                    try {
                        server.ingest(event, up.trusted) { outcome ->
                            when (outcome) {
                                IEventStore.InsertOutcome.Accepted -> accepted.incrementAndGet()
                                is IEventStore.InsertOutcome.Rejected -> rejected.incrementAndGet()
                                is IEventStore.InsertOutcome.Failed -> {
                                    failed.incrementAndGet()
                                    Log.w("MirrorWorker") { "store failed ${event.id}: ${outcome.reason}" }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.w("MirrorWorker") { "catch-up ingest stopped for ${up.url.url}: ${e.message}" }
                        break
                    }
                }
            }

        try {
            var downloaded = 0
            var paged = false
            for (leg in legs) {
                // Reconcile against what we already hold in this leg → download
                // only the diff (like `strfry sync`). No store wired → empty
                // local set → the whole leg is downloaded and the store's
                // unique-id constraint dedups.
                val localEntries = store?.snapshotIdsForNegentropy(listOf(leg)) ?: emptyList()
                // Coverage is stamped from when the local ids were read — that
                // is the state the relay is being compared against.
                val syncStartedAt = TimeUtils.now()
                var seenMin: Long? = null
                var seenMax: Long? = null
                // Per KIND as well as in aggregate: one interval for a
                // multi-kind filter lets a long-lived kind vouch for a
                // short-lived one, and the band then skips the interior for
                // both. The aggregate is still tracked because the reconcile
                // path records against the leg's floor, not per kind.
                val seenByKind = mutableMapOf<Int, SyncCoverage.Span>()

                fun observe(event: Event) {
                    // Same containment as the live path: even a trusted
                    // upstream may only inject events inside the declared scope.
                    if (up.filter == null || up.filter.match(event)) {
                        // Only plausible stamps widen a band — one
                        // misdated event must not discard the rest.
                        if (SyncCoverage.isPlausible(event.createdAt)) {
                            seenMin = minOf(seenMin ?: event.createdAt, event.createdAt)
                            seenMax = maxOf(seenMax ?: event.createdAt, event.createdAt)
                        }
                        SyncCoverage.observe(seenByKind, event.kind, event.createdAt)
                        handoff.trySendBlocking(event)
                    } else {
                        filtered.incrementAndGet()
                    }
                }

                // The two phases run by hand rather than through
                // negentropySyncOrFetch, for two reasons. The combinator keeps
                // every delivered id for cross-phase dedup — a multi-million-
                // event catch-up cannot afford that heap, and the store's
                // unique-id constraint dedups anyway. And the fallback must
                // reset the observed span: a half-finished reconcile delivers
                // events scattered across the whole leg, and a band built from
                // that scatter would claim interior ranges nobody walked.
                val legPaged =
                    try {
                        downloaded +=
                            client
                                .negentropySync(
                                    relay = up.url,
                                    filter = leg,
                                    localEntries = localEntries,
                                    onEvent = ::observe,
                                ).downloaded
                        false
                    } catch (e: NegentropySyncException) {
                        seenMin = null
                        seenMax = null
                        seenByKind.clear()
                        // The watchdog matches negentropySync's default rather
                        // than fetchAllPages' shorter one: a paged catch-up
                        // sits behind the same slow upstreams.
                        downloaded += client.fetchAllPages(up.url, listOf(leg), idleTimeoutMs = 120_000L) { observe(it) }.downloaded
                        true
                    }
                paged = paged || legPaged
                // Recorded per leg, so a failure between legs keeps the ground
                // the first one gained — and no more: a reconcile compared only
                // its own leg, so completeness reaches the leg's ceiling, never
                // "now" while a later leg is still pending. A paged fallback
                // earns only the span it actually saw, capped at the snapshot
                // instant so one future-dated event cannot lift the band's
                // ceiling past what was asked.
                if (legPaged) {
                    coverage?.record(
                        up.url,
                        scopedBase,
                        seenMin,
                        seenMax?.coerceAtMost(syncStartedAt),
                        paged = true,
                        // Capped the same way the aggregate is: one
                        // future-dated event must not lift a kind's ceiling
                        // past what was actually asked for.
                        observedByKind =
                            seenByKind.mapValues { (_, span) ->
                                SyncCoverage.Span(span.min, span.max.coerceAtMost(syncStartedAt))
                            },
                    )
                } else {
                    val legFloor = leg.since ?: initialSince
                    coverage?.record(
                        up.url,
                        scopedBase,
                        // The compared range starts at the leg's floor whether
                        // or not anything was observed there — that is what a
                        // clean reconcile proves.
                        observedMin = minOf(seenMin ?: legFloor, legFloor),
                        observedMax = null,
                        paged = false,
                        reconciledThrough = minOf(leg.until ?: syncStartedAt, syncStartedAt),
                    )
                }
            }
            Log.i("MirrorWorker") {
                val how = if (paged) "paged REQ (upstream has no NIP-77)" else "negentropy"
                val resumed = if (coverage != null && legs.size > 1) " [resumed: ${legs.size} legs outside the synced band]" else ""
                "catch-up from ${up.url.url}: $downloaded events via $how$resumed"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w("MirrorWorker") { "down catch-up from ${up.url.url} failed (live tail continues): ${e.message}" }
        } finally {
            handoff.close()
            consumer.join()
        }
    }

    /**
     * The up half of the NIP-77 "sync" — geode's `strfry sync --dir up`.
     * Reconciles the local set against the upstream and PUSHES the events we
     * hold that the upstream lacks (the reconcile's `have` ids) — the mirror of
     * [runCatchUpDown]. Negentropy-only: if the upstream doesn't speak NIP-77 the
     * reconcile throws and we log; the live up-session (started at `now`) keeps
     * pushing new local events, so this is a non-fatal historical catch-up.
     *
     * Needs [store] to enumerate what we hold; without it there is nothing to
     * reconcile against and the phase is skipped.
     */
    private suspend fun runCatchUpUp(
        up: MirrorUpstream,
        scopedBase: Filter,
        initialSince: Long,
        until: Long,
        exchanged: RecentIds?,
    ) {
        val localStore = store ?: return
        val catchUpFilter = scopedBase.copy(since = initialSince, until = until)
        // Our local set for this window is fixed; each round reconciles it
        // against the upstream, which grows as we push, so the `have` diff
        // shrinks to zero.
        val localEntries = localStore.snapshotIdsForNegentropy(listOf(catchUpFilter))
        try {
            var round = 0
            while (round < MAX_UP_SYNC_ROUNDS) {
                // Reconcile and PUBLISH the `have` ids (events we hold the
                // upstream lacks) as each batch streams in — never materialising
                // the full diff. On a large window the id list is millions of
                // entries; the streaming reconcile keeps memory at one batch and
                // still back-pressures the relay because publishing suspends the
                // round. The `need` direction is the down catch-up's job, so its
                // ids are discarded (not accumulated) here.
                var haveCount = 0
                var pushed = 0
                client.negentropyReconcile(
                    relay = up.url,
                    filter = catchUpFilter,
                    localEntries = localEntries,
                    batchSize = HAVE_FETCH_BATCH,
                    onHaveIds = { batch ->
                        haveCount += batch.size
                        for (event in localStore.query<Event>(Filter(ids = batch))) {
                            // Scope containment: a scoped upstream only receives
                            // in-scope events. Echo suppression: record the id so
                            // a BOTH mirror doesn't re-ingest its own push on the
                            // down sub (but always re-publish — a straggler stays
                            // in `exchanged` yet still needs delivering).
                            if (up.filter != null && !up.filter.match(event)) continue
                            exchanged?.add(event.id)
                            client.publish(event, setOf(up.url))
                            pushed++
                        }
                        // Pace the outbox so a batch drains before the next.
                        delay(UP_PUBLISH_PACING_MS)
                    },
                    onNeedIds = { },
                )
                if (haveCount == 0) {
                    Log.i("MirrorWorker") { "up catch-up to ${up.url.url}: converged after $round round(s)" }
                    return
                }
                // `client.publish`'s outbox is best-effort under a bulk burst
                // (each publish also churns a reconnect), so instead of trusting
                // one pass we re-reconcile next round and re-push only what didn't
                // land — the reconcile is the delivery check, so the push
                // converges to lossless.
                sentUp.addAndGet(pushed.toLong())
                Log.i("MirrorWorker") { "up catch-up to ${up.url.url}: round $round pushed $pushed (had $haveCount to go)" }
                round++
                // Let the upstream ingest + OK before the next reconcile, so the
                // diff reflects what actually landed rather than what's in flight.
                delay(UP_SYNC_SETTLE_MS)
            }
            Log.w("MirrorWorker") { "up catch-up to ${up.url.url}: did not fully converge in $MAX_UP_SYNC_ROUNDS rounds (live push continues)" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w("MirrorWorker") { "up catch-up to ${up.url.url} failed (live push continues): ${e.message}" }
        }
    }

    /** Down direction: subscribe to the upstream, ingest what it sends. */
    private fun startDown(
        index: Int,
        up: MirrorUpstream,
        scopedBase: Filter,
        initialSince: Long,
        exchanged: RecentIds?,
    ): DownSub {
        // watermark tracks the newest created_at ingested from this
        // upstream; seeded at initialSince so a still-catching-up
        // backfill can never advance the since backwards.
        val watermark = AtomicLong(initialSince)
        val listener =
            object : SubscriptionListener {
                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    // The trusted identity is the relay THIS subscription
                    // dialed. The pool dispatches EVENTs by subscription
                    // id alone and every upstream shares this one client,
                    // so a hostile co-configured upstream could answer
                    // with another subscription's id and ride its trust —
                    // bind the decision to the delivering relay, not the
                    // sub id.
                    if (relay != up.url) {
                        filtered.incrementAndGet()
                        Log.w("MirrorWorker") { "dropped event delivered by ${relay.url} on ${up.url.url}'s subscription: ${event.id}" }
                        return
                    }
                    // strfry-router parity: never take the upstream's
                    // word for what matched. Re-checking the configured
                    // scope here means even a trusted (skip-verify)
                    // upstream can only inject events the operator
                    // declared — the REQ shapes what we ask for, this
                    // shapes what we accept.
                    if (up.filter != null && !up.filter.match(event)) {
                        filtered.incrementAndGet()
                        Log.d("MirrorWorker") { "out-of-scope from ${relay.url}: ${event.id}" }
                        return
                    }
                    // BOTH: an event we just pushed up is fanned back on
                    // this subscription — it already exists locally.
                    if (exchanged?.contains(event.id) == true) return
                    exchanged?.add(event.id)
                    // Advance the reconnect watermark past this event.
                    watermark.updateAndGet { if (event.createdAt > it) event.createdAt else it }
                    inbound.trySend(Inbound(event, up.trusted))
                }

                override fun onCannotConnect(
                    relay: NormalizedRelayUrl,
                    message: String,
                    forFilters: List<Filter>?,
                ) {
                    Log.w("MirrorWorker") { "cannot reach upstream ${relay.url}: $message" }
                }
            }
        val subId = "geode-mirror-$index"
        client.subscribe(
            subId = subId,
            filters = mapOf(up.url to listOf(scopedBase.copy(since = initialSince))),
            listener = listener,
        )
        return DownSub(subId, up, scopedBase, listener, initialSince, watermark)
    }

    /**
     * Up direction: an in-process session on the LOCAL relay subscribes
     * with the same scoped filter — stored replay covers the backfill
     * window, the live tail covers everything after — and each matching
     * event is handed to the client's outbox for [MirrorUpstream.url].
     * The outbox owns delivery: it re-sends on reconnect until the
     * upstream OKs, and the upstream's own duplicate handling absorbs
     * replays. Going through a real session (not the store) means the
     * relay's policy chain gates what leaves, same as any client.
     */
    private fun startUp(
        up: MirrorUpstream,
        scopedFilter: Filter,
        exchanged: RecentIds?,
    ) {
        val session =
            server.connect { json ->
                if (!json.startsWith("[\"EVENT\"")) return@connect
                val event =
                    runCatching { (OptimizedJsonMapper.fromJsonToMessage(json) as? EventMessage)?.event }
                        .getOrNull() ?: return@connect
                // BOTH: don't push back what we just pulled down.
                if (exchanged?.contains(event.id) == true) return@connect
                exchanged?.add(event.id)
                client.publish(event, setOf(up.url))
                sentUp.incrementAndGet()
            }
        upSessions += AutoCloseable { session.close() }
        scope.launch {
            session.receive(OptimizedJsonMapper.toJson(ReqCmd("geode-mirror-up", listOf(scopedFilter))))
        }
    }

    /**
     * Stops pulling from every upstream. In-flight ingest submissions
     * drain through the server's queue; events still buffered in
     * [inbound] are dropped — the next boot's `since` overlaps only if
     * the operator configured a backfill window, which is the documented
     * trade-off of a live mirror.
     */
    override fun close() {
        // Close the client first so no listener callback races the
        // channel close below.
        upSessions.forEach { runCatching { it.close() } }
        runCatching { client.close() }
        inbound.close()
        scope.cancel()
        // Wait (bounded) for the workers to land: a coverage.record racing
        // past the state file's final flush would be recorded and lost.
        // Bounded because a worker parked in a blocking hand-off does not
        // feel the cancel, and shutdown must not hang on it.
        runBlocking {
            withTimeoutOrNull(CLOSE_JOIN_MS) { scope.coroutineContext[Job]?.join() }
        }
        okhttp?.dispatcher?.executorService?.shutdown()
        okhttp?.connectionPool?.evictAll()
    }

    private companion object {
        /** How long [close] waits for the worker coroutines to land. */
        const val CLOSE_JOIN_MS = 5_000L

        /** Matches the Android app's relay-pool WebSocket ping interval. */
        const val PING_INTERVAL_SECS = 120L

        /** How often the retry pump nudges disconnected upstreams. */
        const val RECONNECT_POKE_MS = 5_000L

        /**
         * Overlap (seconds) subtracted from the reconnect `since`
         * watermark. Re-requests a small tail on reconnect to cover
         * out-of-order streaming and clock skew; the store's unique-id
         * constraint drops the duplicates. Bounds worst-case replay after
         * a flap to this window instead of everything since boot.
         */
        const val WATERMARK_OVERLAP_SECS = 300L

        /**
         * Per-upstream echo-suppression LRU size (BOTH direction only).
         * Covers the burst window between pulling an event down and the
         * up-session seeing its local fanout; eviction only costs a
         * duplicate round trip.
         */
        const val EXCHANGED_IDS_CAPACITY = 8_192

        /**
         * Depth of the catch-up hand-off between the negentropy download and the
         * ingest consumer. Small: the negentropy download is already internally
         * backpressured, so this only smooths the seam — the bounded IngestQueue
         * behind `server.ingest` is the real limiter.
         */
        const val CATCHUP_HANDOFF = 4_096

        /** Ids per store read when loading up-catch-up events to publish. */
        const val HAVE_FETCH_BATCH = 500

        /** Pause between up-catch-up publish batches so the outbox drains. */
        const val UP_PUBLISH_PACING_MS = 40L

        /** Max reconcile→push rounds before the up catch-up gives up converging. */
        const val MAX_UP_SYNC_ROUNDS = 8

        /** Settle time between an up-push and the next verifying reconcile. */
        const val UP_SYNC_SETTLE_MS = 1_500L
    }
}

/**
 * [leg] intersected with the boot window `[since, until]`, or null when the
 * band already covers everything this window could ask. Coverage legs come
 * off the stable scoped filter and are unbounded on one side; the catch-up
 * only ever asks inside its own window.
 */
internal fun clampToWindow(
    leg: Filter,
    since: Long,
    until: Long,
): Filter? {
    val newSince = maxOf(leg.since ?: since, since)
    val newUntil = minOf(leg.until ?: until, until)
    if (newSince > newUntil) return null
    return leg.copy(since = newSince, until = newUntil)
}
