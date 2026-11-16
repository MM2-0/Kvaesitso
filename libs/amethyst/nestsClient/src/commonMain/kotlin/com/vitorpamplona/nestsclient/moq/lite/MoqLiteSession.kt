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
package com.vitorpamplona.nestsclient.moq.lite

import com.vitorpamplona.nestsclient.moq.MoqCodecException
import com.vitorpamplona.nestsclient.moq.MoqWriter
import com.vitorpamplona.nestsclient.trace.NestsTrace
import com.vitorpamplona.nestsclient.trace.jsonStr
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import com.vitorpamplona.quic.Varint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * moq-lite (Lite-03) session for both listener and publisher roles.
 * Wraps a connected [WebTransportSession] and exposes:
 *
 * Listener side:
 *   - [announce] — open an Announce bidi with a prefix, observe live
 *     `Active` / `Ended` updates from the relay.
 *   - [subscribe] — open a Subscribe bidi for a `(broadcast, track)`
 *     pair, await SubscribeOk, return a [MoqLiteSubscribeHandle] whose
 *     [MoqLiteSubscribeHandle.frames] yields each frame the publisher
 *     pushes.
 *
 * Publisher side:
 *   - [publish] — claim a broadcast suffix; the session then services
 *     every relay-opened Announce / Subscribe bidi automatically and
 *     returns a [MoqLitePublisherHandle] the application can push
 *     Opus frames into. Group rollover is the application's call —
 *     [MoqLitePublisherHandle.send] auto-starts a group on first call,
 *     [MoqLitePublisherHandle.endGroup] FINs and starts a new one on
 *     the next send.
 *
 * Wire-protocol scope: Lite-03 — no SETUP, no datagrams, one fresh
 * bidi per request (subscriber → publisher OR publisher accepts from
 * relay), group data on uni streams. See
 * `nestsClient/plans/2026-04-26-moq-lite-gap.md` for the byte-level
 * layout.
 */
class MoqLiteSession internal constructor(
    /**
     * `internal` (not `private`) so test code in the same module can
     * downcast to a platform [WebTransportSession] (typically
     * `QuicWebTransportSession`) and read diagnostic flow-control
     * counters from the underlying QUIC connection. Production code
     * paths inside this file continue to use `transport` as before.
     */
    internal val transport: WebTransportSession,
    private val scope: CoroutineScope,
) {
    /**
     * Wire-format version this session speaks. Derived from the
     * WebTransport ALPN the server selected via
     * `wt-available-protocols` / `wt-protocol`
     * ([WebTransportSession.negotiatedSubProtocol]); falls back to
     * [MoqLiteVersion.LITE_03] when the server didn't echo
     * `wt-protocol` or echoed something we don't recognise (older
     * single-protocol deployments / forward-compat).
     *
     * Selects between the version-conditional codec branches in
     * [MoqLiteCodec] for `Announce.hops`, `AnnouncePlease.excludeHop`,
     * and `Probe.rtt`. All other messages are wire-identical between
     * the two versions. Tests drive this via
     * `FakeWebTransport.pair(negotiatedSubProtocol = …)`.
     */
    val version: MoqLiteVersion =
        MoqLiteVersion.fromAlpn(transport.negotiatedSubProtocol) ?: MoqLiteVersion.LITE_03
    private val state = Mutex()
    private val subscriptionsBySubscribeId: MutableMap<Long, ListenerSubscription> = HashMap()
    private var nextSubscribeId: Long = 0L
    private var groupPump: Job? = null

    /** Lazily-launched relay→us inbound bidi pump; only runs while a publisher is active. */
    private var bidiPump: Job? = null

    /**
     * Single shared announce-watch pump that runs while we have any
     * listener-side subscription. Closes the frames channel of any
     * subscription whose broadcast suffix goes Ended on the relay's
     * announce stream — see [pumpAnnounceWatch] for why this is the
     * only reliable signal of publisher disconnect under moq-lite
     * Lite-03. Lazily launched on first subscribe; lives until the
     * session scope is cancelled.
     */
    private var announceWatchJob: Job? = null

    /**
     * Pumps of the announce bidis [announce] opened, guarded by [state]. They run on the caller's
     * scope, which outlives this session across token recycles and reconnects, so [close] cancels
     * them instead of waiting for the transport to end their streams.
     */
    private val announcePumps = mutableListOf<Job>()

    /**
     * Active publishers on this session. moq-lite models a broadcast as
     * `(suffix, set-of-tracks)`; the relay multiplexes Subscribe
     * messages to whichever publisher claims the requested track. We
     * allow N concurrent publishers per session as long as they share
     * the same suffix (= same broadcast) and have distinct tracks.
     *
     * The nests audio room use case needs at least two: `audio/data`
     * for Opus frames and `catalog.json` for the broadcast metadata
     * the canonical kixelated/moq watcher subscribes to first to
     * discover what's available. Without the catalog the broadcast is
     * invisible to the JS reference watcher (the browser-side nests
     * web client) — Amethyst-to-Amethyst kept working only because
     * both sides hardcoded the audio track name and skipped discovery.
     */
    private val activePublishers: MutableList<PublisherStateImpl> = mutableListOf()

    @Volatile private var closed: Boolean = false

    val isClosed: Boolean get() = closed

    /**
     * Open an Announce bidi against the relay. Sends
     * `ControlType=Announce` + `AnnouncePlease(prefix)`, then surfaces
     * a flow of [MoqLiteAnnounce] updates the relay streams back.
     *
     * The returned [MoqLiteAnnouncesHandle] keeps the bidi open until
     * [MoqLiteAnnouncesHandle.close] is called. FINing the bidi is
     * how moq-lite signals "no longer interested" — there is no
     * ANNOUNCE_CANCEL message.
     */
    suspend fun announce(prefix: String): MoqLiteAnnouncesHandle {
        ensureOpen()
        val bidi = transport.openBidiStream()
        bidi.write(Varint.encode(MoqLiteControlType.Announce.code))
        bidi.write(MoqLiteCodec.encodeAnnouncePlease(MoqLiteAnnouncePlease(prefix), version))

        // replay=64, DROP_OLDEST: announces emitted before the
        // caller's `collect` attaches MUST NOT be dropped — that's
        // the late-attach race that left `_announcedSpeakers` empty
        // in production receiver logs even after a clear Active
        // arrived (the session-internal pumpAnnounceWatch beat the
        // VM-level `observeAnnounces` to subscribing, then the
        // VM-level bidi's pump emitted to a SharedFlow with no
        // collector yet, and the prior `replay=0` silently dropped
        // it). Replay buffer covers up to 64 distinct announces —
        // far more than nests rooms have speakers — and DROP_OLDEST
        // means the bidi pump never has to suspend on emit, so
        // backpressure can't propagate up into the QUIC read loop.
        val updates =
            MutableSharedFlow<MoqLiteAnnounce>(
                replay = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        Log.d("NestRx") { "session.announce(prefix='$prefix') bidi opened, pump launching (replayCap=64)" }
        NestsTrace.emit("announce_bidi_opened") { "\"prefix\":${jsonStr(prefix)}" }
        val pump =
            scope.launch {
                val buffer = MoqLiteFrameBuffer()
                var chunkCount = 0
                var emitCount = 0
                try {
                    bidi.incoming().collect { chunk ->
                        chunkCount += 1
                        buffer.push(chunk)
                        while (true) {
                            val payload = buffer.readSizePrefixed() ?: break
                            val decoded = MoqLiteCodec.decodeAnnounce(payload, version)
                            emitCount += 1
                            Log.d("NestRx") {
                                "session.announce(prefix='$prefix') bidi pump emit #$emitCount " +
                                    "status=${decoded.status} suffix='${decoded.suffix.take(12)}' " +
                                    "(chunks=$chunkCount)"
                            }
                            NestsTrace.emit("announce_pump_emit") {
                                "\"prefix\":${jsonStr(prefix)}," +
                                    "\"emit_count\":$emitCount," +
                                    "\"status\":${jsonStr(decoded.status.toString())}," +
                                    "\"suffix\":${jsonStr(decoded.suffix)}," +
                                    "\"chunks\":$chunkCount"
                            }
                            updates.emit(decoded)
                        }
                    }
                    Log.w("NestRx") { "session.announce(prefix='$prefix') bidi.incoming() ended naturally (chunks=$chunkCount, emits=$emitCount)" }
                    NestsTrace.emit("announce_bidi_ended_naturally") {
                        "\"prefix\":${jsonStr(prefix)},\"chunks\":$chunkCount,\"emits\":$emitCount"
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w("NestRx") { "announce(prefix='$prefix'): bidi.incoming() threw ${t::class.simpleName}: ${t.message} (chunks=$chunkCount, emits=$emitCount)" }
                    NestsTrace.emit("announce_bidi_threw") {
                        "\"prefix\":${jsonStr(prefix)},\"chunks\":$chunkCount,\"emits\":$emitCount," +
                            "\"error\":${jsonStr(t::class.simpleName ?: "?")}," +
                            "\"message\":${jsonStr(t.message ?: "")}"
                    }
                    // Flow terminated (peer FIN or transport close).
                    // The Announce stream's emit-side just stops; consumers
                    // see an end-of-flow.
                }
            }
        // A close() that ran while this pump was starting has already taken the list: cancel the
        // pump here, or nothing would.
        val cancelNow =
            state.withLock {
                if (closed) {
                    true
                } else {
                    announcePumps.removeAll { it.isCompleted }
                    announcePumps += pump
                    false
                }
            }
        if (cancelNow) pump.cancel()
        return MoqLiteAnnouncesHandle(
            updates = updates,
            close = {
                runCatching { bidi.finish() }
                pump.cancelAndJoin()
            },
        )
    }

    /**
     * Open a Subscribe bidi for `(broadcast, track)`. Sends
     * `ControlType=Subscribe` + the [MoqLiteSubscribe] body, awaits
     * the [MoqLiteCodec.SubscribeResponse] reply, and on Ok returns a
     * [MoqLiteSubscribeHandle] whose `frames` flow yields each frame
     * the publisher pushes through the relay (one [MoqLiteFrame] per
     * payload, bundled into [MoqLiteFrame] structs that carry the
     * group sequence).
     *
     * Throws [MoqLiteSubscribeException] if the publisher / relay
     * replies with Drop, or if the response stream tears down before
     * any reply arrives.
     */
    suspend fun subscribe(
        broadcast: String,
        track: String,
        priority: Int = DEFAULT_PRIORITY,
        ordered: Boolean = true,
        maxLatencyMillis: Long = 0L,
        startGroup: Long? = null,
        endGroup: Long? = null,
    ): MoqLiteSubscribeHandle {
        ensureOpen()
        // Open the announce-watch bidi BEFORE the subscribe goes
        // out. moq-rs uses the announce stream to propagate
        // broadcast availability into the subscriber session — a
        // subscribe that arrives before our session has any
        // announce bidi open is rejected with "not found", even
        // when the publisher's session is alive on the relay. The
        // bidi must be on the wire before subscribe sends; lazy-
        // launching after subscribe (the obvious-but-wrong shape)
        // races the relay's discovery and produces flaky misses,
        // especially for fresh listener sessions opened after a
        // wrapper-driven reconnect.
        ensureAnnounceWatchStarted()
        val id =
            state.withLock {
                check(!closed) { "session is closed" }
                val next = nextSubscribeId++
                next
            }
        val request =
            MoqLiteSubscribe(
                id = id,
                broadcast = broadcast,
                track = track,
                priority = priority,
                ordered = ordered,
                maxLatencyMillis = maxLatencyMillis,
                startGroup = startGroup,
                endGroup = endGroup,
            )
        val bidi = transport.openBidiStream()

        // Single long-running collector for the bidi's whole lifetime.
        // The collector parses the SubscribeResponse inline, signals it
        // via [responseDeferred], then continues draining the bidi
        // until the peer FINs (publisher disconnect under moq-lite
        // Lite-03) or the transport tears down. On collector exit,
        // the frames channel is closed so the consumer's
        // `frames.consumeAsFlow()` ends naturally.
        //
        // Why one collector (vs separate response read + lifetime
        // watch): the underlying QUIC stream's `incoming` is backed
        // by `Channel<ByteArray>.consumeAsFlow()` which CANCELS the
        // channel when the first collect ends. A second collect on a
        // fresh `bidi.incoming()` Flow would see the already-cancelled
        // channel and fire prematurely.
        //
        // Why we need a lifetime watch at all: pumpAnnounceWatch only
        // closes frames when the relay actively sends Announce(Ended).
        // A silent transport black-hole never reaches that path; without
        // a per-subscription bidi watch, consumers would hang forever
        // on dead UDP paths.
        val frames = Channel<MoqLiteFrame>(capacity = DEFAULT_FRAME_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val responseDeferred = CompletableDeferred<MoqLiteCodec.SubscribeResponse>()

        // Pre-register the subscription BEFORE launching the collector
        // AND before the SUBSCRIBE goes on the wire. The order matters
        // for two reasons:
        //
        //   1. Collector race (original reason): if the publisher FINs
        //      the bidi immediately after sending Ok, the collector's
        //      exit-cleanup races subscribe()'s post-await registration
        //      — collector exits, runs `remove(id)` against an empty
        //      map, no-ops; subscribe() then inserts; the frames
        //      channel is now in the map with no live collector to
        //      close it on transport tear-down. Consumer hangs
        //      forever. Pre-registering means the collector's
        //      idempotent remove+close cleanup always finds and
        //      closes the frames channel, regardless of timing.
        //
        //   2. First-group race (audit fix #3): the relay can open
        //      the first group's uni stream BEFORE our subscribe()
        //      continuation re-enters [state] to register `id`. If
        //      the SUBSCRIBE bytes hit the wire before the map entry
        //      lands, [drainOneGroup] looks the id up against an
        //      empty map and silently drops the frame. Registering
        //      the id before writing the SUBSCRIBE closes the
        //      window — by the time the relay can have parsed the
        //      message and opened a uni stream in response, the map
        //      already has our entry.
        val sub =
            ListenerSubscription(
                id = id,
                request = request,
                bidi = bidi,
                frames = frames,
            )
        state.withLock {
            subscriptionsBySubscribeId[id] = sub
            if (groupPump == null) groupPump = scope.launch { pumpUniStreams() }
        }
        Log.d("NestRx") { "SUBSCRIBE send id=$id broadcast='$broadcast' track='$track' maxLatencyMs=$maxLatencyMillis" }
        NestsTrace.emit("subscribe_send") {
            "\"id\":$id,\"broadcast\":${jsonStr(broadcast)},\"track\":${jsonStr(track)}," +
                "\"max_latency_ms\":$maxLatencyMillis"
        }
        // Now that the subscription is registered, push the SUBSCRIBE
        // bytes. If `bidi.write` throws (transport torn down, peer
        // reset) we'd otherwise leave an orphaned map entry whose
        // frames channel never closes — the response collector hasn't
        // been launched yet so its idempotent cleanup wouldn't run.
        // Roll back explicitly on throw.
        try {
            bidi.write(Varint.encode(MoqLiteControlType.Subscribe.code))
            bidi.write(MoqLiteCodec.encodeSubscribe(request))
        } catch (t: Throwable) {
            Log.w("NestRx") { "SUBSCRIBE write failed id=$id: ${t::class.simpleName}: ${t.message}" }
            state.withLock { subscriptionsBySubscribeId.remove(id) }
            frames.close()
            runCatching { bidi.finish() }
            throw t
        }

        scope.launch {
            val responseBuffer = MoqLiteFrameBuffer()
            var responseParsed = false
            // typeCode hoisted outside the collect lambda — `readVarint`
            // advances `pos` permanently while `readSizePrefixed` rolls
            // its own length-varint back on incomplete-body but does NOT
            // roll back the type-code that's already been consumed. If
            // the response chunks split between type and body (possible
            // under MTU pressure / fragmentation), a per-tick `val
            // typeCode = readVarint()` would read the body's size
            // prefix as the type code on the next chunk and misframe
            // the response. Mirrors the same fix in [handleInboundBidi].
            var typeCode: Long? = null
            try {
                bidi.incoming().collect { chunk ->
                    if (!responseParsed) {
                        responseBuffer.push(chunk)
                        if (typeCode == null) typeCode = responseBuffer.readVarint()
                        val tc = typeCode ?: return@collect
                        val body = responseBuffer.readSizePrefixed() ?: return@collect
                        val out = MoqWriter()
                        out.writeVarint(tc)
                        out.writeVarint(body.size.toLong())
                        out.writeBytes(body)
                        val resp = MoqLiteCodec.decodeSubscribeResponse(out.toByteArray())
                        responseDeferred.complete(resp)
                        responseParsed = true
                    }
                    // Post-response chunks are silently discarded —
                    // moq-lite leaves the bidi idle post-Ok. The signal
                    // we care about is the flow's natural completion.
                }
            } catch (ce: CancellationException) {
                if (!responseDeferred.isCompleted) responseDeferred.completeExceptionally(ce)
                throw ce
            } catch (t: Throwable) {
                Log.w("NestRx") { "SUBSCRIBE bidi collector threw id=$id: ${t::class.simpleName}: ${t.message}" }
                if (!responseDeferred.isCompleted) responseDeferred.completeExceptionally(t)
            }
            if (!responseDeferred.isCompleted) {
                responseDeferred.completeExceptionally(
                    MoqLiteSubscribeException("subscribe stream FIN before reply for id=$id"),
                )
            }
            // Idempotent: if subscribe() unwound on a Dropped response
            // (or any throw from await), it already removed the
            // subscription before throwing. Either way: remove + close.
            val removed = state.withLock { subscriptionsBySubscribeId.remove(id) }
            if (removed != null) {
                Log.w("NestRx") { "SUBSCRIBE bidi exited, closing frames id=$id broadcast='${removed.request.broadcast}' track='${removed.request.track}'" }
                NestsTrace.emit("subscribe_bidi_exited") {
                    "\"id\":$id,\"broadcast\":${jsonStr(removed.request.broadcast)}," +
                        "\"track\":${jsonStr(removed.request.track)}"
                }
            }
            removed?.frames?.close()
        }

        val resp =
            try {
                responseDeferred.await()
            } catch (t: Throwable) {
                state.withLock { subscriptionsBySubscribeId.remove(id) }
                frames.close()
                runCatching { bidi.finish() }
                throw t
            }
        when (resp) {
            is MoqLiteCodec.SubscribeResponse.Dropped -> {
                Log.w("NestRx") {
                    "SUBSCRIBE_DROP id=$id broadcast='$broadcast' track='$track' " +
                        "errCode=${resp.drop.errorCode} reason='${resp.drop.reasonPhrase}'"
                }
                NestsTrace.emit("subscribe_drop") {
                    "\"id\":$id,\"broadcast\":${jsonStr(broadcast)},\"track\":${jsonStr(track)}," +
                        "\"err_code\":${resp.drop.errorCode}," +
                        "\"reason\":${jsonStr(resp.drop.reasonPhrase)}"
                }
                state.withLock { subscriptionsBySubscribeId.remove(id) }
                frames.close()
                runCatching { bidi.finish() }
                throw MoqLiteSubscribeException(
                    "publisher rejected subscribe id=$id: errorCode=${resp.drop.errorCode} " +
                        "reason='${resp.drop.reasonPhrase}'",
                )
            }

            is MoqLiteCodec.SubscribeResponse.Ok -> {
                Log.d("NestRx") { "SUBSCRIBE_OK id=$id broadcast='$broadcast' track='$track'" }
                NestsTrace.emit("subscribe_ok") {
                    "\"id\":$id,\"broadcast\":${jsonStr(broadcast)},\"track\":${jsonStr(track)}"
                }
                return MoqLiteSubscribeHandle(
                    id = id,
                    ok = resp.ok,
                    frames = frames.consumeAsFlow(),
                    unsubscribeAction = { unsubscribe(id) },
                    broadcastEndedCountProbe = { broadcastEndedCounts[broadcast] ?: 0L },
                )
            }
        }
    }

    /**
     * Lock that serializes lazy-launch of the announce-watch.
     * Distinct from [state] so the synchronous `announce(prefix="")`
     * inside [ensureAnnounceWatchStarted] can suspend without
     * blocking other state-mutating operations.
     */
    private val announceWatchLock = Mutex()

    /**
     * `Ended` announces seen per broadcast suffix, behind
     * [SubscribeHandle.broadcastEndedCount]. Written by the announce pump,
     * read without suspending by handle probes on other coroutines.
     */
    private val broadcastEndedCounts = ConcurrentHashMap<String, Long>()

    /**
     * Open the shared announce-watch bidi *synchronously* (and
     * launch its collector coroutine) if it isn't already running.
     * Idempotent. Called from [subscribe] before the subscribe
     * message goes on the wire so moq-rs has a chance to propagate
     * broadcast availability into our session before the subscribe
     * arrives — see the comment in [subscribe].
     */
    private suspend fun ensureAnnounceWatchStarted() {
        announceWatchLock.withLock {
            if (announceWatchJob != null) return
            val handle =
                try {
                    announce(prefix = "")
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Couldn't open the announce bidi — best effort,
                    // bail. Subscriptions still work; we just lose
                    // automatic cycle detection (the wrapper still
                    // re-issues on listener swap / explicit failure).
                    return
                }
            announceWatchJob =
                scope.launch {
                    try {
                        pumpAnnounceWatch(handle)
                    } finally {
                        announceWatchLock.withLock { announceWatchJob = null }
                    }
                }
        }
    }

    /**
     * Single shared announce-watch pump for ALL subscriptions on
     * this session. Driven by the bidi opened in
     * [ensureAnnounceWatchStarted]. For each
     * [MoqLiteAnnounceStatus.Ended] update, iterates the
     * subscription map and closes the frames channel of any
     * subscription whose `broadcast` matches the announce suffix.
     * The closed channel ends the consumer-facing
     * `frames.consumeAsFlow()` flow naturally — same shape as a
     * user-driven `handle.unsubscribe()` from the consumer's POV —
     * which lets the wrapper's re-issuance pump drive a fresh
     * subscribe against the same broadcast path. moq-lite supports
     * subscribe-before-announce, so a subscribe issued during the
     * gap (between Ended and the next Active under the same suffix)
     * attaches cleanly when the new publisher comes up.
     *
     * This pump survives announce-bidi errors via best-effort
     * silence — the session itself recovers via its own reconnect
     * path. Cancelled when [scope] is cancelled (session close).
     */
    private suspend fun pumpAnnounceWatch(handle: MoqLiteAnnouncesHandle) {
        try {
            handle.updates.collect { update ->
                Log.d("NestRx") { "ANNOUNCE update status=${update.status} suffix='${update.suffix}' hops=${update.hops}" }
                NestsTrace.emit("announce_watch_update") {
                    "\"status\":${jsonStr(update.status.toString())}," +
                        "\"suffix\":${jsonStr(update.suffix)}," +
                        "\"hops\":${update.hops}"
                }
                if (update.status != MoqLiteAnnounceStatus.Ended) return@collect
                // Count it before anything else. The relay usually FINs the
                // subscribe bidi in the same instant, which closes `frames`
                // on its own path, so a subscription can end before this
                // runs; the count is what tells the next subscription that
                // the publisher session behind the old one is gone.
                broadcastEndedCounts.merge(update.suffix, 1L, Long::plus)
                val targets =
                    state.withLock {
                        subscriptionsBySubscribeId.values
                            .filter { it.request.broadcast == update.suffix }
                            .toList()
                    }
                if (targets.isNotEmpty()) {
                    Log.w("NestRx") { "ANNOUNCE Ended for suffix='${update.suffix}' → closing ${targets.size} subscription(s): ${targets.map { "id=${it.id} track='${it.request.track}'" }}" }
                    NestsTrace.emit("announce_watch_ended_closing_subs") {
                        "\"suffix\":${jsonStr(update.suffix)}," +
                            "\"closed_count\":${targets.size}"
                    }
                }
                for (sub in targets) {
                    // Just close the frames channel — the
                    // wrapper-level collect of `frames.consumeAsFlow()`
                    // ends naturally and the wrapper pump re-issues.
                    // Don't fire `unsubscribe(id)` here: that'd FIN
                    // OUR side of the (still-alive) subscribe bidi,
                    // and the wrapper's re-issue would have to open
                    // a fresh bidi anyway. Keeping the subscribe
                    // bidi open lets a future subscribe-before-
                    // announce land cleanly.
                    sub.frames.close()
                    state.withLock { subscriptionsBySubscribeId.remove(sub.id) }
                    runCatching { sub.bidi.finish() }
                }
            }
            Log.w("NestRx") { "ANNOUNCE pump: updates flow ended naturally" }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w("NestRx") { "ANNOUNCE pump threw ${t::class.simpleName}: ${t.message}" }
            // Announce bidi died — same best-effort fallback.
        } finally {
            runCatching { handle.close() }
        }
    }

    /**
     * Drain inbound uni streams and route each one's group frames to
     * the matching subscription. The relay opens a fresh uni stream
     * per (subscribe id, group sequence); the first byte is
     * [MoqLiteDataType] (0 = Group), followed by a size-prefixed
     * [MoqLiteGroupHeader], followed by `varint(size) + payload`
     * frames until QUIC FIN.
     *
     * One pump per session — started lazily on the first subscribe.
     */
    private suspend fun pumpUniStreams() {
        Log.d("NestRx") { "pumpUniStreams started" }
        NestsTrace.emit("uni_pump_started")
        var streamCount = 0L
        try {
            // coroutineScope binds each per-stream drain to this pump's
            // job — when the pump is cancelled (session close), every
            // drain is cancelled with it instead of leaking as a
            // sibling on the outer [scope] until the transport's flow
            // independently errors out.
            kotlinx.coroutines.coroutineScope {
                transport.incomingUniStreams().collect { stream ->
                    val n = ++streamCount
                    launch { drainOneGroup(stream, n) }
                }
            }
            Log.w("NestRx") { "pumpUniStreams: incomingUniStreams flow ended naturally after $streamCount streams" }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w("NestRx") { "pumpUniStreams ended after $streamCount streams with ${t::class.simpleName}: ${t.message}" }
            // Transport closed — subscriptions will surface end-of-flow
            // via their own bidi pumps as well.
        }
    }

    private suspend fun drainOneGroup(
        stream: com.vitorpamplona.nestsclient.transport.WebTransportReadStream,
        streamSeq: Long,
    ) {
        val buffer = MoqLiteFrameBuffer()
        var typeRead = false
        var subscribeId: Long = -1L
        var groupSequence: Long = -1L
        var headerRead = false
        var frameCount = 0
        var droppedNoSub = 0
        var trySendFailures = 0
        // Audit M2: once we observe the subscription is gone, fire
        // STOP_SENDING(SUBSCRIPTION_GONE) once on the uni stream so
        // the publisher abandons any in-flight retransmits. Pre-fix
        // we silently dropped every frame; the publisher kept pushing
        // and the relay kept buffering bytes the listener would never
        // read. Latched so concurrent frames don't fire it N times
        // (RFC 9000 §3.5: subsequent STOP_SENDING calls are ignored
        // by the peer, but we save the syscall).
        var stopSendingFired = false
        try {
            stream.incoming().collect { chunk ->
                buffer.push(chunk)
                if (!typeRead) {
                    val type = buffer.readVarint() ?: return@collect
                    if (type != MoqLiteDataType.Group.code) {
                        throw MoqCodecException("unknown moq-lite uni-stream type code: $type")
                    }
                    typeRead = true
                }
                if (!headerRead) {
                    val payload = buffer.readSizePrefixed() ?: return@collect
                    val hdr = MoqLiteCodec.decodeGroupHeader(payload)
                    subscribeId = hdr.subscribeId
                    groupSequence = hdr.sequence
                    headerRead = true
                    Log.d("NestRx") { "drainOneGroup#$streamSeq header subId=$subscribeId groupSeq=$groupSequence" }
                    NestsTrace.emit("group_header") {
                        "\"stream_seq\":$streamSeq,\"sub_id\":$subscribeId,\"group_seq\":$groupSequence"
                    }
                }
                while (true) {
                    val frame = buffer.readSizePrefixed() ?: break
                    val sub = state.withLock { subscriptionsBySubscribeId[subscribeId] }
                    if (sub == null) {
                        droppedNoSub += 1
                        if (!stopSendingFired) {
                            stopSendingFired = true
                            Log.w("NestRx") {
                                "drainOneGroup#$streamSeq subId=$subscribeId no longer subscribed — stopSending(SUBSCRIPTION_GONE)"
                            }
                            NestsTrace.emit("group_stop_sending") {
                                "\"stream_seq\":$streamSeq,\"sub_id\":$subscribeId,\"group_seq\":$groupSequence," +
                                    "\"code\":${MoqLiteStreamCancelCode.SUBSCRIPTION_GONE}"
                            }
                            runCatching { stream.stopSending(MoqLiteStreamCancelCode.SUBSCRIPTION_GONE) }
                        }
                    } else {
                        val sent =
                            sub.frames.trySend(
                                MoqLiteFrame(
                                    groupSequence = groupSequence,
                                    payload = frame,
                                ),
                            )
                        if (!sent.isSuccess) trySendFailures += 1
                    }
                    frameCount += 1
                }
            }
            Log.d("NestRx") { "drainOneGroup#$streamSeq FIN subId=$subscribeId groupSeq=$groupSequence frames=$frameCount droppedNoSub=$droppedNoSub trySendFail=$trySendFailures stopSent=$stopSendingFired" }
            NestsTrace.emit("group_fin") {
                "\"stream_seq\":$streamSeq,\"sub_id\":$subscribeId,\"group_seq\":$groupSequence," +
                    "\"frames\":$frameCount,\"dropped_no_sub\":$droppedNoSub,\"try_send_fail\":$trySendFailures," +
                    "\"stop_sent\":$stopSendingFired"
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w("NestRx") { "drainOneGroup#$streamSeq threw subId=$subscribeId groupSeq=$groupSequence frames=$frameCount: ${t::class.simpleName}: ${t.message}" }
            NestsTrace.emit("group_threw") {
                "\"stream_seq\":$streamSeq,\"sub_id\":$subscribeId,\"group_seq\":$groupSequence," +
                    "\"frames\":$frameCount," +
                    "\"error\":${jsonStr(t::class.simpleName ?: "?")}," +
                    "\"message\":${jsonStr(t.message ?: "")}"
            }
            // Stream errored / FIN'd. Nothing to do — the next group
            // arrives on a fresh uni stream.
        }
    }

    private suspend fun unsubscribe(id: Long) {
        val sub =
            state.withLock { subscriptionsBySubscribeId.remove(id) } ?: return
        runCatching { sub.bidi.finish() }
        sub.frames.close()
    }

    /**
     * Open a Probe bidi against the relay / publisher (audit L3).
     * Mirrors kixelated's `Subscriber::run_probe_stream`
     * (`rs/moq-lite/src/lite/subscriber.rs`): subscriber writes
     * `ControlType::Probe` (varint 4), then reads size-prefixed
     * [MoqLiteProbe] messages until the publisher FINs.
     *
     * The publisher reciprocates by writing one or more [MoqLiteProbe]
     * messages indicating its expected outbound bitrate. Amethyst's
     * own publisher path is fixed-rate Opus and emits exactly one
     * Probe before FIN, but third-party (e.g. browser-side
     * `kixelated/moq` watcher → relay-served broadcast) publishers
     * MAY emit multiple over time as their ABR estimates change.
     *
     * The returned handle's `updates` flow is cold — collecting it
     * starts the read pump on this session's scope. On collector
     * cancellation OR [MoqLiteProbeHandle.close], the bidi FINs and
     * the pump exits.
     *
     * No application-level use of probe data exists in nests today
     * (we don't run ABR), but the API completes the moq-lite Lite-03
     * protocol surface. Useful for diagnostic tools that want to
     * read a publisher's bitrate without subscribing to its data
     * track.
     */
    suspend fun probe(): MoqLiteProbeHandle {
        ensureOpen()
        val bidi = transport.openBidiStream()
        bidi.write(Varint.encode(MoqLiteControlType.Probe.code))
        val updates =
            MutableSharedFlow<MoqLiteProbe>(
                replay = 8,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val pump =
            scope.launch {
                val buffer = MoqLiteFrameBuffer()
                try {
                    bidi.incoming().collect { chunk ->
                        buffer.push(chunk)
                        while (true) {
                            val payload = buffer.readSizePrefixed() ?: break
                            updates.emit(MoqLiteCodec.decodeProbe(payload, version))
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Probe bidi died — best-effort silent. The
                    // updates flow simply stops emitting.
                }
            }
        return MoqLiteProbeHandle(
            updates = updates,
            close = {
                runCatching { bidi.finish() }
                pump.cancelAndJoin()
            },
        )
    }

    // ====================================================================
    // Publisher side
    // ====================================================================

    /**
     * Begin publishing under [broadcastSuffix]. Returns a
     * [MoqLitePublisherHandle] that:
     *   - announces `Active` to every relay-opened Announce bidi whose
     *     `AnnouncePlease.prefix` matches our suffix
     *   - opens a fresh group uni stream when a relay-opened Subscribe
     *     bidi for our broadcast arrives, then forwards [MoqLitePublisherHandle.send]
     *     bytes as `varint(size) + payload` frames until [MoqLitePublisherHandle.endGroup]
     *     or [MoqLitePublisherHandle.close]
     *
     * Wire-flow per `kixelated/moq-rs/rs/moq-lite/src/lite/publisher.rs:40+`:
     *   - the relay opens Announce + Subscribe bidi streams *to* us
     *     (`Stream::accept(session)`), so the session's
     *     [WebTransportSession.incomingBidiStreams] pump is the entry
     *     point
     *   - we open uni streams ourselves to push group data
     *     (`session.open_uni()`)
     *
     * Multiple [publish] calls are supported on the same session as
     * long as every call shares the same `broadcastSuffix` (you publish
     * one broadcast per session, with multiple tracks) and uses a
     * distinct `track`. Re-publishing the same `(suffix, track)` pair
     * or mixing different suffixes is rejected with
     * [IllegalStateException].
     *
     * @param startSequence first group sequence the publisher will
     *   assign. Defaults to 0 for fresh broadcasts. The hot-swap path
     *   in [com.vitorpamplona.nestsclient.MoqLiteNestsSpeaker.openPublisherForHotSwap]
     *   passes the previous publisher's
     *   [MoqLitePublisherHandle.nextSequence] so the new session's
     *   group lineage continues monotonically across JWT refreshes —
     *   kixelated/hang's `Container.Consumer.#run` drops any group
     *   with `sequence < #active`, so a reset to 0 after a recycle
     *   silences the watcher until `#active` rolls over.
     */
    suspend fun publish(
        broadcastSuffix: String,
        track: String,
        startSequence: Long = 0L,
        /**
         * Per-track priority byte, 0..255. Defaults to
         * [DEFAULT_TRACK_PRIORITY] (the moq-lite midpoint). Mirrors the
         * `track.priority u8` field that kixelated's
         * `Publisher::serve_group` mixes with `sequence` via its
         * `PriorityHandle` (see `rs/moq-lite/src/lite/priority.rs`).
         * Higher values drain ahead of lower ones under congestion;
         * within a single track, newer groups (higher sequence) drain
         * ahead of older ones. For audio rooms the default is fine —
         * we only run one track at the same priority — but a future
         * multi-track broadcast (e.g. mixing audio with a low-rate
         * status track) can lift audio above its peer.
         */
        trackPriority: Int = DEFAULT_TRACK_PRIORITY,
    ): MoqLitePublisherHandle {
        ensureOpen()
        require(startSequence >= 0L) { "startSequence must be >= 0, got $startSequence" }
        require(trackPriority in 0..255) { "trackPriority must fit in a byte: $trackPriority" }
        val normalised = MoqLitePath.normalize(broadcastSuffix)
        val publisher: PublisherStateImpl
        state.withLock {
            check(!closed) { "session is closed" }
            val existingSuffix = activePublishers.firstOrNull()?.suffix
            check(existingSuffix == null || existingSuffix == normalised) {
                "MoqLiteSession.publish suffix mismatch: existing='$existingSuffix', new='$normalised'. " +
                    "moq-lite models one broadcast per session — open a new session for a different broadcast."
            }
            check(activePublishers.none { it.track == track }) {
                "MoqLiteSession.publish called twice for the same track '$track' on suffix '$normalised'."
            }
            publisher =
                PublisherStateImpl(
                    suffix = normalised,
                    track = track,
                    startSequence = startSequence,
                    trackPriority = trackPriority,
                )
            activePublishers += publisher
            // Lazy launch — the inbound-bidi pump needs to keep running
            // for the lifetime of any active publisher.
            if (bidiPump == null) bidiPump = scope.launch { pumpInboundBidis() }
        }
        return publisher
    }

    /**
     * Drain inbound bidi streams (relay → us) and dispatch each by
     * its leading [MoqLiteControlType] varint. The relay opens
     * Announce / Subscribe bidis on its own initiative; we read the
     * control type, the request body, and reply on the same bidi.
     *
     * One pump per session — started lazily on the first [publish].
     */
    private suspend fun pumpInboundBidis() {
        try {
            // Bind each per-bidi handler to this pump's job (see
            // [pumpUniStreams]'s identical comment) so they don't outlive
            // bidiPump.cancelAndJoin() in [close].
            kotlinx.coroutines.coroutineScope {
                transport.incomingBidiStreams().collect { bidi ->
                    launch { handleInboundBidi(bidi) }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w("NestTx") { "pumpInboundBidis ended with ${t::class.simpleName}: ${t.message}" }
            // Transport closed.
        }
    }

    /**
     * Reject an inbound Subscribe with a typed error code. Writes the
     * `SubscribeDrop` body so a watcher that decodes the application
     * message sees the typed reason, then `RESET_STREAM(errorCode)`s
     * the bidi so a watcher inspecting only the QUIC layer also sees
     * the same code (audit M3 — Lite-03 conveys errors via
     * RESET_STREAM, distinguishing "publisher rejected" from
     * "publisher gracefully shut down" / FIN). Errors during the
     * write/reset are swallowed: the bidi is already on its way out.
     */
    private suspend fun rejectSubscribe(
        bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream,
        errorCode: Long,
        reason: String,
    ) {
        runCatching {
            bidi.write(
                MoqLiteCodec.encodeSubscribeDrop(
                    MoqLiteSubscribeDrop(errorCode = errorCode, reasonPhrase = reason),
                ),
            )
            bidi.reset(errorCode)
        }
    }

    private suspend fun handleInboundBidi(bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream) {
        // Single long-running collector for the bidi's full lifetime.
        // Pre-fix this dispatch was split into a `firstOrNull()` to
        // peek the control byte + a `readSizePrefixedFromBidiInto`
        // to read the body — but `bidi.incoming()` is backed by
        // `Channel<ByteArray>.consumeAsFlow(consume=true)`, which
        // CANCELS the channel when the first collect ends. Any
        // attempt to re-collect from the bidi (e.g. to watch for
        // subscriber-disconnect FIN) saw an immediately-empty
        // closed flow, firing the cleanup right after registration
        // and starving the publisher's send path. With one collector
        // for the bidi's whole life, the dispatch reads the message,
        // the collector continues silently until peer FIN, and the
        // post-collect cleanup runs exactly once — same shape the
        // moq-lite session's [announce] pump already uses.
        val buffer = MoqLiteFrameBuffer()
        // typeCode is hoisted outside the collect lambda so it
        // survives across invocations — `buffer.readVarint()`
        // advances `pos`, so calling it again on the next collect
        // tick would read body bytes as if they were the control
        // varint and tear the dispatch state apart.
        var typeCode: Long? = null
        var dispatched = false
        var inboundSub: MoqLiteSubscribe? = null
        // Track which publisher this bidi was routed to so the peer-FIN
        // cleanup at the bottom calls removeInboundSubscription on the
        // right one. `inboundAnnouncePublisher` is currently unused for
        // cleanup (announce bidis live until publisher.close fires
        // Ended), but we keep the assignment for symmetry / future
        // explicit teardown.
        var inboundSubPublisher: PublisherStateImpl? = null

        @Suppress("UNUSED_VARIABLE")
        var inboundAnnouncePublisher: PublisherStateImpl? = null
        try {
            bidi.incoming().collect { chunk ->
                buffer.push(chunk)
                if (!dispatched) {
                    if (typeCode == null) typeCode = buffer.readVarint()
                    val tc = typeCode ?: return@collect
                    val controlType =
                        MoqLiteControlType.fromCode(tc) ?: run {
                            dispatched = true
                            runCatching { bidi.finish() }
                            return@collect
                        }
                    // Read the publisher list FRESH at dispatch time
                    // rather than at bidi-arrival time. Closes a window
                    // where a publisher registered after the inbound
                    // bidi opened but before its first chunk landed
                    // wouldn't be visible to the dispatcher — pre-fix
                    // we snapshotted the list at the top of
                    // [handleInboundBidi], so the second-track publisher
                    // (e.g. catalog after audio in [MoqLiteNestsSpeaker])
                    // raced the relay's SUBSCRIBE bidi for that track
                    // when registration happened in the ~few-ms gap.
                    // Production never bit because both nests publishers
                    // register before any subscriber arrives, but the
                    // narrower contract is safer.
                    //
                    // All publishers on a single session share a suffix
                    // (enforced in [publish]), so the Announce response
                    // is the same regardless of which publisher we route
                    // the bidi to. Subscribe responses pick the publisher
                    // whose `track` matches `sub.track`.
                    val publishersSnapshot = state.withLock { activePublishers.toList() }
                    if (publishersSnapshot.isEmpty()) {
                        runCatching { bidi.finish() }
                        dispatched = true
                        return@collect
                    }
                    // Designated publisher for Announce-bidi ownership:
                    // the first one. The Active/Ended pair is keyed off
                    // the broadcast SUFFIX (not per track), so emitting
                    // it once via one publisher matches the single-
                    // broadcast model the relay expects. All publishers
                    // close together in [close], so there's no risk of
                    // one closing while the announce bidi stays alive
                    // on another.
                    val announcePublisher = publishersSnapshot.first()
                    when (controlType) {
                        MoqLiteControlType.Announce -> {
                            val pleasePayload = buffer.readSizePrefixed() ?: return@collect
                            val please = MoqLiteCodec.decodeAnnouncePlease(pleasePayload, version)
                            // Per moq-lite Lite-03 (`rs/moq-lite/src/lite/announce.rs`),
                            // a publisher MUST only emit Active for broadcasts whose
                            // path starts with the requested prefix. If our suffix
                            // doesn't match, FIN cleanly without writing any
                            // Announce — the relay/peer sees an empty announce
                            // stream and moves on. Pre-fix we'd fall through the
                            // `null` branch of stripPrefix and falsely emit
                            // `Active(suffix=ourFullSuffix)`, advertising under a
                            // prefix the subscriber didn't ask for. Production
                            // never bit because the relay always asks for
                            // `prefix=""`, but a future peer-to-peer or
                            // namespace-scoped subscriber would see a ghost.
                            val emittedSuffix = MoqLitePath.stripPrefix(please.prefix, announcePublisher.suffix)
                            if (emittedSuffix == null) {
                                Log.w("NestTx") {
                                    "ANNOUNCE inbound prefix='${please.prefix}' does not match publisher.suffix='${announcePublisher.suffix}' — FIN without Active"
                                }
                                runCatching { bidi.finish() }
                                dispatched = true
                                return@collect
                            }
                            bidi.write(
                                MoqLiteCodec.encodeAnnounce(
                                    MoqLiteAnnounce(
                                        status = MoqLiteAnnounceStatus.Active,
                                        suffix = emittedSuffix,
                                        hops = emptyList(),
                                    ),
                                    version,
                                ),
                            )
                            announcePublisher.registerAnnounceBidi(bidi, emittedSuffix)
                            // Routed to publisher that owns the announce bidi for the lifecycle
                            // — for the multi-track use case (audio + catalog), all share one
                            // suffix and one announce bidi pointing at the first publisher.
                            inboundAnnouncePublisher = announcePublisher
                            Log.d("NestTx") { "ANNOUNCE inbound prefix='${please.prefix}' → emitted Active suffix='$emittedSuffix' (publisher.suffix='${announcePublisher.suffix}')" }
                            dispatched = true
                        }

                        MoqLiteControlType.Subscribe -> {
                            val subPayload = buffer.readSizePrefixed() ?: return@collect
                            val sub = MoqLiteCodec.decodeSubscribe(subPayload)
                            // Validate the requested broadcast matches our
                            // session's publisher suffix BEFORE matching
                            // tracks — a peer subscribing under the wrong
                            // broadcast must not be able to siphon our audio
                            // by guessing a track name. Both sides have
                            // already path-normalised at the codec wire
                            // boundary (`MoqLiteCodec.decodeSubscribe` calls
                            // `MoqLitePath.normalize`), so a direct equality
                            // is sufficient. Reject with
                            // BROADCAST_DOES_NOT_EXIST so the peer sees a
                            // typed error code rather than a silent FIN.
                            // Publishers in `publishersSnapshot` all share
                            // the same suffix (enforced by
                            // [MoqLiteSession.publish]), so checking the
                            // first is equivalent to checking all.
                            val ourSuffix = announcePublisher.suffix
                            if (sub.broadcast != ourSuffix) {
                                Log.w("NestTx") {
                                    "SUBSCRIBE inbound id=${sub.id} broadcast='${sub.broadcast}' does not match " +
                                        "publisher.suffix='$ourSuffix' — replying SubscribeDrop+RESET"
                                }
                                rejectSubscribe(
                                    bidi = bidi,
                                    errorCode = MoqLiteSubscribeDropCode.BROADCAST_DOES_NOT_EXIST,
                                    reason =
                                        "broadcast '${sub.broadcast}' is not published on this session " +
                                            "(we publish '$ourSuffix')",
                                )
                                dispatched = true
                                return@collect
                            }
                            // Find the publisher that claims this track. With the
                            // single-track-per-publisher model, only one match is possible.
                            val targetPublisher = publishersSnapshot.firstOrNull { it.track == sub.track }
                            if (targetPublisher == null) {
                                Log.w("NestTx") {
                                    "SUBSCRIBE inbound id=${sub.id} track='${sub.track}' has no matching publisher " +
                                        "on this session (have ${publishersSnapshot.map { it.track }}) — replying SubscribeDrop+RESET"
                                }
                                rejectSubscribe(
                                    bidi = bidi,
                                    errorCode = MoqLiteSubscribeDropCode.TRACK_DOES_NOT_EXIST,
                                    reason =
                                        "track '${sub.track}' is not published on this broadcast " +
                                            "(available: ${publishersSnapshot.joinToString(",") { it.track }})",
                                )
                                dispatched = true
                                return@collect
                            }
                            Log.d("NestTx") { "SUBSCRIBE inbound id=${sub.id} broadcast='${sub.broadcast}' track='${sub.track}' (publisher track='${targetPublisher.track}')" }
                            // Register the subscription BEFORE sending Ok so the
                            // peer's observation of Ok is a happens-after of
                            // `inboundSubs += sub`. Otherwise on dispatchers that
                            // resume the peer's `bidi.incoming().first()`
                            // continuation before this coroutine's continuation
                            // (notably Windows under Dispatchers.Default), the
                            // peer's first `publisher.send` after Ok races the
                            // registration and observes an empty subscriber set.
                            targetPublisher.registerInboundSubscription(sub)
                            inboundSub = sub
                            inboundSubPublisher = targetPublisher
                            // Audit L2: narrow `startGroup` to the
                            // publisher's [MoqLitePublisherHandle.nextSequence]
                            // — the exact sequence of the next group we'll
                            // open. Spec lets the publisher narrow the
                            // subscriber's bounds; pre-fix we always echoed
                            // `null/null`, which loses the diagnostic
                            // information of "which group are you about to
                            // start sending?" Useful for hot-swap
                            // continuations where `nextSequence` is non-zero
                            // (the seeded `startSequence` from the previous
                            // session). `endGroup` stays null because we're
                            // a live broadcast with no end in sight; the
                            // subscriber's request `endGroup` is honoured
                            // implicitly when the publisher closes.
                            val narrowedStart = targetPublisher.nextSequence
                            bidi.write(
                                MoqLiteCodec.encodeSubscribeOk(
                                    MoqLiteSubscribeOk(
                                        priority = sub.priority,
                                        ordered = sub.ordered,
                                        maxLatencyMillis = sub.maxLatencyMillis,
                                        startGroup = narrowedStart,
                                        endGroup = null,
                                    ),
                                ),
                            )
                            Log.d("NestTx") { "SUBSCRIBE_OK id=${sub.id} narrowed startGroup=$narrowedStart" }
                            dispatched = true
                        }

                        MoqLiteControlType.Probe -> {
                            // Subscriber-opened bidi asking us (the
                            // publisher) for a bitrate hint. Per
                            // `lite/probe.rs:Lite03+`, the publisher
                            // writes one or more size-prefixed
                            // `Probe { bitrate: u62 }` messages on
                            // this bidi. We're a fixed-rate Opus
                            // publisher, so emit a single hint and
                            // FIN our write side — the peer treats
                            // FIN as "no further updates" rather than
                            // an error. Better than the old behaviour
                            // of FINing without writing anything,
                            // which left the subscriber's ABR
                            // estimator with no signal.
                            runCatchingCancellable {
                                bidi.write(MoqLiteCodec.encodeProbe(MoqLiteProbe(bitrate = NESTS_AUDIO_BITRATE_HINT_BPS), version))
                                bidi.finish()
                            }
                            dispatched = true
                        }

                        MoqLiteControlType.Fetch -> {
                            // Subscriber-opened bidi requesting a
                            // historical group (`lite/fetch.rs`).
                            // Audio rooms are live-only — we have no
                            // group history to serve. FINing the
                            // write side without any reply is the
                            // spec-clean way to signal "no groups
                            // available"; the subscriber's wait on
                            // its receive side resolves to
                            // end-of-stream and it falls back to a
                            // live Subscribe. Ignoring inbound bytes
                            // (the request body) is fine: we don't
                            // need to know which group was requested
                            // because we couldn't serve any of them.
                            runCatching { bidi.finish() }
                            dispatched = true
                        }

                        MoqLiteControlType.Session -> {
                            // ControlType=0 was the Lite-01/02 setup
                            // exchange. Lite-03 doesn't use it; if a
                            // legacy peer sends one, FIN cleanly so
                            // their bidi resolves rather than hanging.
                            runCatching { bidi.finish() }
                            dispatched = true
                        }

                        MoqLiteControlType.Goaway -> {
                            // Relay's graceful-shutdown signal — see
                            // [MoqLiteControlType.Goaway]. We don't
                            // act on the migration request today
                            // (no body decode, no preferred-relay
                            // failover); the `connectReconnecting*`
                            // wrappers' transport-loss reconnect path
                            // already handles the eventual hard
                            // disconnect, so all this arm needs to do
                            // is recognise the type code and FIN
                            // cleanly instead of treating it as an
                            // unknown control. Logged so a relay-
                            // initiated migration shows up in logcat
                            // rather than as a mystery silent reconnect.
                            Log.w("NestRx") { "Goaway received from relay — FIN bidi (no migration handler today)" }
                            runCatching { bidi.finish() }
                            dispatched = true
                        }
                    }
                }
                // Post-dispatch chunks are silently discarded —
                // Lite-03's announce / subscribe bidis are idle
                // after the response. The signal we care about is
                // the flow's natural completion (peer FIN =
                // subscriber-disconnect, or transport drop).
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // Bidi errored — fall through to the same cleanup.
        }
        // Flow ended (peer FIN or error). Remove the inbound
        // subscribe so the publisher's send path stops keying new
        // groups off this dead subscriber. Announce bidis are
        // owned by the publisher state for sending Ended on
        // publisher-close — we don't remove them here.
        val sub = inboundSub
        val pub = inboundSubPublisher
        if (sub != null && pub != null) {
            pub.removeInboundSubscription(sub)
        }
    }

    /**
     * Open a new uni stream for one group's frames and push the
     * `DataType=Group` byte + size-prefixed [MoqLiteGroupHeader].
     * Returns a write stream the caller frames each Opus packet onto.
     */
    internal suspend fun openGroupStream(
        subscribeId: Long,
        sequence: Long,
        trackPriority: Int = DEFAULT_TRACK_PRIORITY,
    ): com.vitorpamplona.nestsclient.transport.WebTransportWriteStream {
        // Group streams use reliable QUIC delivery to match the
        // moq-lite reference (kixelated/moq-rs `serve_group` writes
        // reliable streams; bestEffort is not a moq-lite concept).
        // The previous shape opened with `bestEffort=true`, which
        // caused `:quic`'s `SendBuffer.markLost` to drop lost ranges
        // without retransmit AND without RESET_STREAM — leaving the
        // peer's stream-reassembly buffer permanently wedged at the
        // hole boundary. The watcher's `Group.readFrame` parks until
        // the relay's 30 s `MAX_GROUP_AGE` ages the broadcast queue
        // out, manifesting as a 30 s silent dropout per lost packet
        // on lossy networks. Reliable delivery costs marginal extra
        // bandwidth on retransmits (a lost STREAM range arriving 50–
        // 150 ms late still falls inside hang's default ~200 ms
        // jitter buffer) and avoids the dropout entirely.
        val uni = transport.openUniStream()
        // Mirror moq-rs `Publisher::serve_group` (`rs/moq-lite/src/lite/
        // publisher.rs`) and `PriorityHandle.insert(track.priority,
        // sequence)`: priority sorts first by track (higher track =
        // drains ahead under congestion), then by group sequence within
        // a track (newer = drains ahead). Pre-fix we passed raw
        // `sequence` and ignored the per-track byte entirely, which
        // worked for our single-track Opus case but starves a low-rate
        // companion track (e.g. catalog) the moment audio's outbound
        // queue gets congested.
        //
        // Wire layout into `Int`:
        //   bit 31       0 (always — keeps the value non-negative)
        //   bits 30..23  trackPriority u8 (0..255)
        //   bits 22..0   sequence low 23 bits  (~8.4M groups)
        //
        // Bit 31 is reserved as the sign bit because
        // [com.vitorpamplona.quic.connection.QuicConnectionWriter] sorts
        // streams via signed `sortedByDescending { it.priority }`.
        // Setting bit 31 (which would happen for `trackPriority >= 0x80`
        // shifted by 24) produces a negative `Int` that sorts BELOW
        // positive priorities, inverting the intent. The audit's
        // priority-byte default `DEFAULT_TRACK_PRIORITY = 0x80` sits
        // exactly on this boundary — without the 23-bit shift the
        // default-vs-default case would be fine but a hand-tuned 0xFF
        // (highest intended) would sort behind 0x7F (lower intended).
        //
        // Saturating cast on `sequence` guards a theoretical broadcast
        // long enough to outgrow 23 bits (≈ 97 days at the production
        // 1-group/sec cadence). Past that the priority degrades to "all
        // newer groups within a track tie", but they still beat older
        // groups of any LOWER-priority track via the high byte.
        // Production sessions cycle on JWT refresh every 9 min, so the
        // wrap is defensive only.
        val seqLow = sequence.coerceAtMost(SEQ_MAX.toLong()).toInt() and SEQ_MASK
        val packedPriority = ((trackPriority and 0xFF) shl SEQ_BITS) or seqLow
        uni.setPriority(packedPriority)
        uni.write(Varint.encode(MoqLiteDataType.Group.code))
        uni.write(MoqLiteCodec.encodeGroupHeader(MoqLiteGroupHeader(subscribeId, sequence)))
        return uni
    }

    suspend fun close() {
        if (closed) return
        closed = true
        val toClose: List<ListenerSubscription>
        val publishersToClose: List<PublisherStateImpl>
        val announcePumpsToCancel: List<Job>
        state.withLock {
            toClose = subscriptionsBySubscribeId.values.toList()
            subscriptionsBySubscribeId.clear()
            publishersToClose = activePublishers.toList()
            activePublishers.clear()
            announcePumpsToCancel = announcePumps.toList()
            announcePumps.clear()
        }
        for (sub in toClose) {
            runCatching { sub.bidi.finish() }
            sub.frames.close()
        }
        for (p in publishersToClose) {
            runCatching { p.close() }
        }
        groupPump?.cancelAndJoin()
        bidiPump?.cancelAndJoin()
        announceWatchJob?.cancel()
        announcePumpsToCancel.forEach { it.cancel() }
        runCatching { transport.close() }
    }

    private fun ensureOpen() {
        check(!closed) { "MoqLiteSession is closed" }
    }

    private class ListenerSubscription(
        val id: Long,
        val request: MoqLiteSubscribe,
        val bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream,
        val frames: Channel<MoqLiteFrame>,
    )

    /**
     * Publisher state. Tracks the announce bidis the relay opened to us
     * + the inbound subscriptions a relay (or peer) opened against our
     * broadcast for [track], and owns the current group's uni stream.
     *
     * Per moq-lite Lite-03 a publisher is responsible for one
     * `(broadcast, track)` tuple — the relay multiplexes multiple
     * tracks per broadcast by routing each inbound SUBSCRIBE to the
     * publisher whose track field matches. Subs whose `sub.track`
     * doesn't match this publisher's [track] are intentionally
     * ignored so a listener subscribing to e.g. `catalog.json` while
     * we're only publishing `audio/data` doesn't accidentally hijack
     * the audio routing — see [registerInboundSubscription] for the
     * filter and the bug history below.
     *
     * Bug history (`nestsClient/plans/2026-05-04-publisher-track-routing.md`):
     * before this filter, [openNextGroupLocked] keyed each group
     * stream off `inboundSubs.first()`. When a listener opened both a
     * `catalog.json` subscribe and an `audio/data` subscribe, whichever
     * arrived first won the routing race — and because the catalog
     * SUBSCRIBE typically races ahead of the audio one by a few ms,
     * every Opus frame ended up on the catalog stream. Listeners saw
     * a perpetually-spinning speaker avatar with no audio.
     *
     * `gate` serialises access to per-group state so concurrent
     * `send` / `startGroup` / `endGroup` / `close` can't race.
     */
    private inner class PublisherStateImpl(
        override val suffix: String,
        internal val track: String,
        startSequence: Long,
        /**
         * Per-track priority byte (0..255) — see [publish] kdoc.
         * Mixed with each group's [GroupOutbound.sequence] in
         * [openGroupStream] to mirror kixelated's `(track.priority,
         * sequence)` priority ordering.
         */
        internal val trackPriority: Int,
    ) : MoqLitePublisherHandle {
        private val gate = Mutex()
        private val announceBidis = mutableListOf<AnnounceBidiEntry>()
        private val inboundSubs = mutableListOf<MoqLiteSubscribe>()
        private var currentGroup: GroupOutbound? = null

        // `@Volatile` so the hot-swap caller can read this from outside
        // the publisher's gate (see [MoqLitePublisherHandle.nextSequence]
        // kdoc). Mutation happens only inside [openNextGroupLocked]
        // (which holds [gate]); the volatile guarantees a cross-thread
        // read sees the latest write without contending the gate.
        @Volatile
        private var nextSequenceField: Long = startSequence

        override val nextSequence: Long
            get() = nextSequenceField

        // Diagnostic: throttled counter for "send returned false" logs so a
        // long no-subscriber window doesn't flood logcat at 50 Hz.
        private val sendNoSubLogCount =
            java.util.concurrent.atomic
                .AtomicLong(0L)

        @Volatile private var publisherClosed = false

        /**
         * Caller-installed hook fired once per accepted inbound
         * SUBSCRIBE — see [MoqLitePublisherHandle.setOnNewSubscriber].
         * Read-and-fire happens OUTSIDE [gate] to avoid deadlocking on
         * the hook's own calls to [send] / [endGroup].
         */
        @Volatile private var onNewSubscriberHook: (suspend () -> Unit)? = null

        override fun setOnNewSubscriber(hook: (suspend () -> Unit)?) {
            onNewSubscriberHook = hook
        }

        suspend fun registerAnnounceBidi(
            bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream,
            emittedSuffix: String,
        ) {
            gate.withLock {
                if (publisherClosed) {
                    // publisher.close() raced ahead — write Ended now so
                    // the relay sees the proper lifecycle close on this bidi.
                    runCatching {
                        bidi.write(
                            MoqLiteCodec.encodeAnnounce(
                                MoqLiteAnnounce(
                                    status = MoqLiteAnnounceStatus.Ended,
                                    suffix = emittedSuffix,
                                    hops = emptyList(),
                                ),
                                version,
                            ),
                        )
                    }
                    runCatching { bidi.finish() }
                    return
                }
                announceBidis += AnnounceBidiEntry(bidi, emittedSuffix)
            }
        }

        suspend fun registerInboundSubscription(sub: MoqLiteSubscribe) {
            // Capture the hook INSIDE the lock — guarantees the hook
            // observes a fully-registered subscriber when it fires —
            // but invoke it OUTSIDE so a hook that calls [send] /
            // [endGroup] doesn't deadlock on the same gate.
            var hookToFire: (suspend () -> Unit)? = null
            gate.withLock {
                if (publisherClosed) {
                    Log.w("NestTx") { "SUBSCRIBE inbound rejected (publisher closed) id=${sub.id} track='${sub.track}'" }
                    return
                }
                if (sub.track != track) {
                    Log.w("NestTx") { "SUBSCRIBE inbound track mismatch id=${sub.id} sub.track='${sub.track}' publisher.track='$track' — ignored" }
                    return
                }
                inboundSubs += sub
                hookToFire = onNewSubscriberHook
                Log.d("NestTx") { "SUBSCRIBE registered id=${sub.id} broadcast='${sub.broadcast}' track='${sub.track}' inboundSubs.size=${inboundSubs.size}" }
            }
            // Launch on the session's scope so the hook outlives the
            // bidi pump's per-bidi coroutine if it's slow (e.g. a
            // catalog send blocked on transport backpressure).
            hookToFire?.let { hook ->
                scope.launch { runCatchingCancellable { hook.invoke() } }
            }
        }

        /**
         * Remove an inbound subscription whose bidi was FIN'd by the
         * relay (subscriber disconnected). FINs the current group
         * defensively because [openNextGroupLocked] keys each uni
         * stream off `inboundSubs.first()`'s id; if the dropped sub
         * was first, the current uni stream is dead-routed and the
         * next send must open a fresh group keyed off whatever
         * live sub is now first.
         */
        suspend fun removeInboundSubscription(sub: MoqLiteSubscribe) {
            gate.withLock {
                if (publisherClosed) return
                if (!inboundSubs.remove(sub)) return
                Log.w("NestTx") { "SUBSCRIBE removed (peer FIN/error) id=${sub.id} track='${sub.track}' inboundSubs.size=${inboundSubs.size}" }
                runCatching { currentGroup?.uni?.finish() }
                currentGroup = null
            }
        }

        override suspend fun startGroup() {
            gate.withLock {
                if (publisherClosed) return
                runCatching { currentGroup?.uni?.finish() }
                currentGroup = openNextGroupLocked()
            }
        }

        override suspend fun send(payload: ByteArray): Boolean {
            gate.withLock {
                if (publisherClosed) {
                    if (sendNoSubLogCount.getAndIncrement() % SEND_LOG_THROTTLE == 0L) {
                        Log.w("NestTx") { "send returning false — publisher closed (count=${sendNoSubLogCount.get()})" }
                    }
                    return false
                }
                if (inboundSubs.isEmpty()) {
                    if (sendNoSubLogCount.getAndIncrement() % SEND_LOG_THROTTLE == 0L) {
                        Log.w("NestTx") { "send returning false — no inboundSubs (count=${sendNoSubLogCount.get()}, payload=${payload.size}B)" }
                    }
                    return false
                }
                val group = currentGroup ?: openNextGroupLocked().also { currentGroup = it }
                // Single-allocation framing: write the varint length
                // directly into a buffer sized for `varint + payload`,
                // then copy the payload after it. The previous shape
                // (`Varint.encode(...) + payload`) allocated twice per
                // Opus frame — once for the varint, once for the `+`
                // concatenation — at 50 fps × N speakers that's measurable
                // young-gen pressure on the audio hot path.
                val payloadSize = payload.size
                val varintLen = Varint.size(payloadSize.toLong())
                val framed = ByteArray(varintLen + payloadSize)
                Varint.writeTo(payloadSize.toLong(), framed, 0)
                payload.copyInto(framed, varintLen)
                val writeResult = runCatching { group.uni.write(framed) }
                if (writeResult.isFailure) {
                    // The uni stream errored (peer reset, transport closed,
                    // FIN'd by removeInboundSubscription). Drop the dead
                    // stream so the next send opens a fresh group instead of
                    // re-trying on a corpse, and surface the failure to the
                    // caller — the prior contract of "always true on
                    // not-muted" silently masked publisher disconnects.
                    runCatching { group.uni.finish() }
                    currentGroup = null
                    currentCoroutineContext().ensureActive()
                    return false
                }
            }
            return true
        }

        override suspend fun endGroup() {
            gate.withLock {
                if (publisherClosed) return
                val group = currentGroup ?: return
                currentGroup = null
                runCatching { group.uni.finish() }
            }
        }

        override suspend fun close() {
            val toFinalise: List<AnnounceBidiEntry>
            val groupToFinish: GroupOutbound?
            gate.withLock {
                if (publisherClosed) return
                publisherClosed = true
                toFinalise = announceBidis.toList()
                announceBidis.clear()
                inboundSubs.clear()
                groupToFinish = currentGroup
                currentGroup = null
            }
            for (entry in toFinalise) {
                runCatching {
                    entry.bidi.write(
                        MoqLiteCodec.encodeAnnounce(
                            MoqLiteAnnounce(
                                status = MoqLiteAnnounceStatus.Ended,
                                suffix = entry.emittedSuffix,
                                hops = emptyList(),
                            ),
                            version,
                        ),
                    )
                }
                runCatching { entry.bidi.finish() }
            }
            runCatching { groupToFinish?.uni?.finish() }
            // Detach from the session so a subsequent `publish` can run.
            state.withLock {
                activePublishers.remove(this@PublisherStateImpl)
            }
        }

        /** Caller holds [gate]. */
        private suspend fun openNextGroupLocked(): GroupOutbound {
            // moq-lite groups are addressed by `subscribeId` on the wire —
            // each inbound subscription gets its own group stream. For
            // simplicity we open one stream per group keyed off the
            // *first* subscription's id; relay-side multi-subscriber
            // fan-out happens above us. Inbound subscription set is
            // expected to be small (1 in nests's listener-per-room
            // model), so this is fine.
            val sub = inboundSubs.first()
            val sequence = nextSequenceField
            nextSequenceField = sequence + 1L
            val uni =
                try {
                    openGroupStream(subscribeId = sub.id, sequence = sequence, trackPriority = trackPriority)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w("NestTx") { "openGroupStream threw subId=${sub.id} seq=$sequence: ${t::class.simpleName}: ${t.message}" }
                    throw t
                }
            Log.d("NestTx") { "openGroupStream subId=${sub.id} seq=$sequence trackPriority=$trackPriority" }
            return GroupOutbound(sequence = sequence, uni = uni)
        }
    }

    private data class AnnounceBidiEntry(
        val bidi: com.vitorpamplona.nestsclient.transport.WebTransportBidiStream,
        val emittedSuffix: String,
    )

    private data class GroupOutbound(
        val sequence: Long,
        val uni: com.vitorpamplona.nestsclient.transport.WebTransportWriteStream,
    )

    companion object {
        /** moq-lite priority byte midpoint — neutral default. */
        const val DEFAULT_PRIORITY: Int = 0x80

        /**
         * Default per-track priority byte applied when [publish] is
         * called without an explicit `trackPriority`. Same midpoint
         * as the subscriber-side [DEFAULT_PRIORITY] — kept distinct
         * because the two values are conceptually independent (one
         * is the subscriber's priority hint in the SUBSCRIBE wire
         * field, the other is the publisher's per-stream drain
         * priority in `kixelated/moq`'s `PriorityHandle`).
         */
        const val DEFAULT_TRACK_PRIORITY: Int = 0x80

        /**
         * Bit layout for the QUIC stream priority value packed by
         * [openGroupStream]. Bit 31 is reserved as the sign bit (so
         * the writer's signed `sortedByDescending` orders correctly);
         * bits 30..23 carry [DEFAULT_TRACK_PRIORITY]-family
         * `trackPriority u8`; bits 22..0 carry the low [SEQ_BITS] of
         * the group sequence. See the kdoc on [openGroupStream] for
         * the full rationale.
         */
        private const val SEQ_BITS: Int = 23
        private const val SEQ_MASK: Int = 0x007F_FFFF
        private const val SEQ_MAX: Int = SEQ_MASK

        /**
         * Bitrate hint (bits/sec) we report on inbound moq-lite Probe
         * bidis as a publisher. Mirrors the upper-bound of an Opus
         * voice profile at 48 kHz mono, ≈32 kbps. Subscriber-side ABR
         * estimators use this to size their forward queue; we emit the
         * single hint and FIN since our encoder runs at a fixed bitrate.
         */
        const val NESTS_AUDIO_BITRATE_HINT_BPS: Long = 32_000L

        // Diagnostic: log "send returned false" once every N invocations.
        // At 50 fps and N=50 → ≤ 1 log/sec for a sustained no-sub window.
        private const val SEND_LOG_THROTTLE: Long = 50L

        /**
         * Per-subscription channel buffer for inbound frames. 128 audio
         * frames at Opus 20 ms ≈ 2.5 s of backlog before DROP_OLDEST,
         * matching a real-time listener's tolerance.
         */
        const val DEFAULT_FRAME_BUFFER: Int = 128

        /**
         * Attach to a [WebTransportSession] in the client role. Lite-03
         * has no SETUP — the WT handshake itself is the handshake — so
         * this returns a ready-to-use session immediately.
         *
         * @param pumpScope where the inbound-uni-stream + per-bidi pumps
         *   live. Production code passes the owning ViewModel scope.
         */
        fun client(
            transport: WebTransportSession,
            pumpScope: CoroutineScope,
        ): MoqLiteSession = MoqLiteSession(transport, pumpScope)
    }
}
