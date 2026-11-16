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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.moq.lite.MoqLiteHangCatalog
import com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * `connectNestsSpeaker` plus a transport-loss reconnect loop with
 * exponential backoff and proactive JWT refresh. Mirror of
 * [connectReconnectingNestsListener] for the publish side.
 *
 * The returned [NestsSpeaker]'s state surfaces the underlying speaker's
 * state directly while a session is alive, but flips to
 * [NestsSpeakerState.Reconnecting] between attempts. The speaker is
 * auto-redirected to the freshly-opened session under the hood —
 * `startBroadcasting()` returns a stable [BroadcastHandle] whose
 * `setMuted` and `close` survive every refresh.
 *
 * **Why this exists** — moq-auth issues 600 s bearer tokens
 * (`moq-auth/src/index.ts`). Without proactive refresh, any room a
 * user keeps the stage in for >10 min hits an authorisation failure
 * the moment the relay tears down the session, the publish stream
 * goes silent, and the user has to manually re-tap "Talk". The
 * proactive recycle keeps the WebTransport session young so the
 * relay never sees an expired token.
 *
 * **Broadcast-handle re-issuance** — the caller-owned
 * [BroadcastHandle] survives a refresh / reconnect. Internally the
 * wrapper opens a fresh underlying `BroadcastHandle` against each
 * new session, replays the user's mute intent on it, and forwards
 * `setMuted` calls to whichever live handle exists at the time.
 * `close` cancels the re-issue pump and best-effort closes the
 * latest live handle.
 *
 * **Audio gap during refresh — eliminated for moq-lite** — when the
 * underlying speaker implements [HotSwappablePublisherSource]
 * (which [MoqLiteNestsSpeaker] does), the wrapper keeps a single
 * long-lived [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster]
 * alive across session recycles and only swaps the
 * [com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle]
 * underneath it. The mic + encoder run continuously through the
 * boundary and the listener hears no silence. The old session
 * close runs in parallel with the new openOnce so the WebTransport
 * teardown doesn't block the swap.
 *
 * **Audio gap during refresh — legacy path** — for speakers that
 * don't implement [HotSwappablePublisherSource] (the IETF reference
 * `DefaultNestsSpeaker` and test fakes), the wrapper falls back to
 * close-then-restart: the listener hears ~50–150 ms of silence per
 * recycle. Acceptable because the IETF path is reference-only —
 * production runs the moq-lite hot-swap path.
 *
 * Cancellation: cancelling [scope] (typically the room screen's VM
 * scope) cancels the reconnect loop and closes both the active
 * session and the active broadcast. [NestsSpeaker.close] is
 * idempotent.
 */
suspend fun connectReconnectingNestsSpeaker(
    httpClient: NestsClient,
    transport: WebTransportFactory,
    scope: CoroutineScope,
    room: NestsRoomConfig,
    signer: NostrSigner,
    speakerPubkeyHex: String,
    captureFactory: () -> AudioCapture,
    encoderFactory: () -> OpusEncoder,
    /**
     * Per-broadcast audio shape, threaded into [connectNestsSpeaker]
     * (and therefore into the catalog payload) AND used by the
     * hot-swap pump's per-session catalog publisher so the wire shape
     * stays consistent across JWT-refresh recycles. Defaults to mono.
     */
    broadcastConfig: AudioBroadcastConfig = AudioBroadcastConfig(),
    policy: NestsReconnectPolicy = NestsReconnectPolicy(),
    /**
     * Proactive JWT refresh window. moq-auth issues bearer tokens
     * with a 600 s lifetime; once the token expires the relay
     * tears down the WebTransport session and we'd otherwise
     * recover via the regular reconnect path with a brief audible
     * dropout AND a permanent broadcast loss until the user taps
     * Talk again. By recycling the session a minute before expiry
     * we stay ahead of the relay's tear-down: the new session
     * opens, the broadcast pump reopens publishing on it (carrying
     * the user's mute intent), and the wrapper's outward state
     * never enters the user-visible Reconnecting state.
     *
     * Set to 0 or negative to disable.
     */
    tokenRefreshAfterMs: Long = 540_000L,
    /**
     * Test seam — defaults to the production [connectNestsSpeaker].
     * Tests pass a fake that returns a scripted [NestsSpeaker] so
     * the reconnect state machine can be exercised without a real
     * WebTransport stack.
     */
    connector: suspend () -> NestsSpeaker = {
        connectNestsSpeaker(
            httpClient = httpClient,
            transport = transport,
            scope = scope,
            room = room,
            signer = signer,
            speakerPubkeyHex = speakerPubkeyHex,
            captureFactory = captureFactory,
            encoderFactory = encoderFactory,
            broadcastConfig = broadcastConfig,
        )
    },
): NestsSpeaker {
    val state = MutableStateFlow<NestsSpeakerState>(NestsSpeakerState.Idle)
    val activeSpeaker = MutableStateFlow<NestsSpeaker?>(null)

    suspend fun openOnce(): NestsSpeaker {
        val speaker = connector()
        activeSpeaker.value = speaker
        state.value = speaker.state.value
        return speaker
    }

    val orchestrator =
        scope.launch {
            var attempt = 0
            while (true) {
                val speaker =
                    try {
                        openOnce()
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        // Propagate so the orchestrator dies promptly on
                        // scope cancellation; `runCatching` would have
                        // eaten the cancel and run one more iteration.
                        throw ce
                    } catch (t: Throwable) {
                        state.value = NestsSpeakerState.Failed("connect failed: ${t.message}", t)
                        null
                    }
                var refreshTriggered = false
                if (speaker != null) {
                    // Wait for either a terminal state OR the proactive
                    // JWT-refresh deadline. withTimeoutOrNull returns
                    // null when the timer fires first; we then close
                    // the (still-healthy) speaker and loop to mint a
                    // fresh JWT via openOnce(). The broadcast-pump
                    // re-issues publishing onto the new session
                    // without the wrapper's outward state ever
                    // showing Reconnecting.
                    //
                    // The `onEach { mirror } + first` pattern (rather
                    // than `state.collect { mirror; if terminal break }`)
                    // is what lets `withTimeoutOrNull` cancel cleanly
                    // mid-mirror — once cancelled, the underlying
                    // speaker's subsequent state changes (e.g. the
                    // Closed we trigger on the next line) don't leak
                    // out to the wrapper's state.
                    val terminalAwait: suspend () -> NestsSpeakerState = {
                        speaker.state
                            .onEach { state.value = it }
                            .first { s ->
                                s is NestsSpeakerState.Failed || s is NestsSpeakerState.Closed
                            }
                    }
                    val terminal =
                        if (tokenRefreshAfterMs > 0L) {
                            withTimeoutOrNull(tokenRefreshAfterMs) { terminalAwait() }
                        } else {
                            terminalAwait()
                        }
                    if (terminal == null) {
                        // Refresh deadline hit before any terminal state —
                        // planned recycle, not a failure. Don't bump
                        // `attempt` (it's not a backoff event) so the next
                        // openOnce() runs immediately.
                        //
                        // Hand the old speaker's close to a separate
                        // launch so it runs IN PARALLEL with the next
                        // iteration's openOnce(). Sequence on the wire:
                        //
                        //   1. (concurrent) old speaker close — closes
                        //      old session's publisher + WebTransport.
                        //   2. (concurrent) new openOnce() → sets
                        //      activeSpeaker = newSpeaker.
                        //   3. Wrapper's hot-swap pump observes the
                        //      activeSpeaker change, opens a publisher
                        //      on the new session, swaps it into the
                        //      long-lived broadcaster, closes the old
                        //      publisher.
                        //
                        // The previous shape closed the old speaker
                        // synchronously BEFORE openOnce, which forced the
                        // mic + encoder to stop and re-open across the
                        // boundary (50–150 ms audible silence at the
                        // listener). With the close hoisted onto a
                        // sibling job, the broadcaster keeps capturing
                        // through the recycle and the listener hears
                        // continuous audio.
                        val toClose = speaker
                        // Best-effort: a close that fails leaves nothing more to do.
                        scope.launch { runCatchingCancellable { toClose.close() } }
                        attempt = 0
                        refreshTriggered = true
                    } else if (terminal is NestsSpeakerState.Failed && !isUserCancelledSpeaker(terminal)) {
                        // Transport-side failure → schedule a reconnect. A Failed speaker is not
                        // closed by anything else: the next openOnce() only replaces
                        // activeSpeaker, so close it here, off the loop like the refresh branch,
                        // or its session, pumps and QUIC connection stay up for as long as the
                        // relay keeps the connection alive.
                        val toClose = speaker
                        // Best-effort: a close that fails leaves nothing more to do.
                        scope.launch { runCatchingCancellable { toClose.close() } }
                        attempt++
                        if (!policy.isExhausted(attempt)) {
                            val delayMs = policy.delayForAttempt(attempt)
                            state.value = NestsSpeakerState.Reconnecting(attempt, delayMs)
                        }
                    }
                }
                if (refreshTriggered) {
                    // Skip the reconnect-schedule path entirely — a
                    // refresh is a planned cutover, not a backoff event.
                    continue
                }
                val terminal = state.value
                if (terminal is NestsSpeakerState.Closed) break
                if (policy.isExhausted(attempt + 1)) break
                val delayMs =
                    if (terminal is NestsSpeakerState.Reconnecting) {
                        terminal.delayMs
                    } else {
                        policy.delayForAttempt(++attempt)
                    }
                state.value = NestsSpeakerState.Reconnecting(attempt.coerceAtLeast(1), delayMs)
                delay(delayMs)
            }
        }

    // Match the existing [connectNestsSpeaker] semantics: suspend
    // until the first session is up (or hard-fails) so the VM's
    // call site `val s = speakerConnector.connect(...); s.startBroadcasting()`
    // keeps working without changes. This is a deliberate departure
    // from the listener wrapper, which returns immediately and
    // expects the VM to gate `subscribeSpeaker` on the Connected
    // state — the speaker side has a tighter `startBroadcasting`
    // contract that requires a live session at call time.
    val firstReady =
        state.first { s ->
            s is NestsSpeakerState.Connected ||
                s is NestsSpeakerState.Broadcasting ||
                s is NestsSpeakerState.Failed
        }
    if (firstReady is NestsSpeakerState.Failed) {
        // Unwind: cancel the orchestrator + close any speaker that
        // managed to open before the failure surfaced.
        orchestrator.cancel()
        runCatching { activeSpeaker.value?.close() }
        throw NestsException(firstReady.reason, firstReady.cause)
    }

    return ReconnectingSpeakerHandle(
        mutableState = state,
        activeSpeaker = activeSpeaker,
        orchestrator = orchestrator,
        scope = scope,
        captureFactory = captureFactory,
        encoderFactory = encoderFactory,
        broadcastConfig = broadcastConfig,
    )
}

private fun isUserCancelledSpeaker(state: NestsSpeakerState.Failed): Boolean {
    val msg = state.reason
    // Forward-compat seam — same shape as [isUserCancelled] on the
    // listener side. User-driven close goes through Closed today;
    // anything else surfaced as Failed is a transport / handshake
    // error worth retrying.
    return msg.contains("user cancelled", ignoreCase = true)
}

private class ReconnectingSpeakerHandle(
    private val mutableState: MutableStateFlow<NestsSpeakerState>,
    private val activeSpeaker: MutableStateFlow<NestsSpeaker?>,
    private val orchestrator: Job,
    private val scope: CoroutineScope,
    /**
     * Audio-pipeline factories propagated from
     * [connectReconnectingNestsSpeaker]. Used by the hot-swap
     * [ReissuingBroadcastHandle] to construct ONE long-lived
     * [NestMoqLiteBroadcaster] that survives session recycles —
     * without these the broadcaster would have to be re-built
     * (which restarts the mic + encoder + adds a 50–150 ms
     * audible gap) on every JWT refresh.
     */
    private val captureFactory: () -> AudioCapture,
    private val encoderFactory: () -> OpusEncoder,
    private val broadcastConfig: AudioBroadcastConfig,
) : NestsSpeaker {
    override val state: StateFlow<NestsSpeakerState> = mutableState.asStateFlow()

    private val gate = Mutex()

    @Volatile private var activeBroadcast: ReissuingBroadcastHandle? = null

    override suspend fun startBroadcasting(onLevel: (Float) -> Unit): BroadcastHandle =
        gate.withLock {
            check(state.value !is NestsSpeakerState.Closed) {
                "startBroadcasting on a closed speaker"
            }
            check(activeBroadcast == null) {
                "speaker is already broadcasting"
            }
            // Require a live (or just-connected) session — matches
            // the listener wrapper's `subscribeSpeaker` contract.
            // The wrapper's own `connect()` already suspended until
            // the first session was up, so this check almost never
            // fails in practice; it guards the second-call-after-
            // close case.
            activeSpeaker.value
                ?: error("no live session — wait for state == Connected before startBroadcasting")

            val handle =
                ReissuingBroadcastHandle(
                    activeSpeaker = activeSpeaker,
                    scope = scope,
                    captureFactory = captureFactory,
                    encoderFactory = encoderFactory,
                    broadcastConfig = broadcastConfig,
                    onLevel = onLevel,
                    onClose = { closed ->
                        if (activeBroadcast === closed) activeBroadcast = null
                    },
                )
            handle.start()
            activeBroadcast = handle
            handle
        }

    /**
     * Force-close the active inner speaker so the orchestrator opens
     * a fresh session against the (presumably new) network. Used by
     * the platform layer on a network change. Mirror of the listener
     * wrapper's [com.vitorpamplona.nestsclient.NestsListener.recycleSession].
     */
    override suspend fun recycleSession() {
        val current = activeSpeaker.value ?: return
        runCatching { current.close() }
    }

    override suspend fun close() {
        orchestrator.cancel()
        runCatching { activeBroadcast?.close() }
        runCatching { activeSpeaker.value?.close() }
        if (mutableState.value !is NestsSpeakerState.Closed) {
            mutableState.value = NestsSpeakerState.Closed
        }
    }
}

/**
 * Stable [BroadcastHandle] backed by a re-issuing pump. Two paths,
 * picked per-iteration based on whether the active speaker exposes
 * the [HotSwappablePublisherSource] hook:
 *
 *  - **Hot-swap path** (moq-lite): the pump constructs ONE
 *    long-lived [NestMoqLiteBroadcaster] on the first session
 *    and, on every subsequent session swap, opens a fresh
 *    [com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle]
 *    on the new session and atomically swaps it into the
 *    broadcaster via
 *    [NestMoqLiteBroadcaster.swapPublisher]. The mic +
 *    encoder + capture loop keep running through the swap, so
 *    listeners hear ZERO gap at the JWT-refresh boundary
 *    (vs the legacy ~50–150 ms close-then-restart silence).
 *
 *  - **Legacy path** (IETF reference / test fakes): each session
 *    swap closes the prior `BroadcastHandle` from
 *    [NestsSpeaker.startBroadcasting] and opens a fresh one. Same
 *    behaviour as before this hot-swap was introduced.
 *
 * `setMuted` updates the cached intent unconditionally; the pump
 * applies the latest intent to whichever underlying broadcaster /
 * handle is currently live, so user-observed mute is monotonic
 * across recycles.
 */
private class ReissuingBroadcastHandle(
    private val activeSpeaker: StateFlow<NestsSpeaker?>,
    private val scope: CoroutineScope,
    private val captureFactory: () -> AudioCapture,
    private val encoderFactory: () -> OpusEncoder,
    /**
     * Per-broadcast audio shape, used to pick the catalog payload the
     * per-session catalog publisher emits. The shape is constant for
     * the wrapper's lifetime (we don't renegotiate mid-broadcast), so
     * caching the payload bytes once on the [opus48kJsonBytes]
     * memoiser is enough.
     */
    private val broadcastConfig: AudioBroadcastConfig,
    /**
     * Forwarded to the underlying broadcaster (hot-swap path) or
     * `sp.startBroadcasting` (legacy path) so the local-speaking ring
     * keeps animating across session recycles. Mirrors how
     * [desiredMuted] is replayed — the user-observed signal is
     * monotonic.
     */
    private val onLevel: (Float) -> Unit,
    private val onClose: (ReissuingBroadcastHandle) -> Unit,
) : BroadcastHandle {
    @Volatile private var desiredMuted: Boolean = false

    @Volatile private var closed: Boolean = false

    /** Hot-swap path's long-lived broadcaster. Null until the first session arrives. */
    @Volatile private var hotSwapBroadcaster: NestMoqLiteBroadcaster? = null

    /**
     * Hot-swap path's per-session catalog publisher and its periodic
     * republish job. Catalog has no long-lived encoder pipeline (unlike
     * audio), so each session gets a fresh publisher + republish loop;
     * cleanup happens at the start of the next iteration after the new
     * pair is installed (mirroring how the old audio publisher is
     * closed only after [NestMoqLiteBroadcaster.swapPublisher] returns
     * it). Both nullable so [close] can no-op when no iteration ever
     * ran (i.e. wrapper closed before the first session connected).
     */
    @Volatile private var hotSwapCatalogPublisher: MoqLitePublisherHandle? = null

    /** Legacy path's per-session handle. Cleared when the session swaps. */
    private val liveHandle = AtomicReference<BroadcastHandle?>(null)
    private var pumpJob: Job? = null

    override val isMuted: Boolean get() = desiredMuted

    fun start() {
        // Per-iteration pump: every time activeSpeaker changes, decide
        // whether the new speaker supports hot swap. If yes, retarget
        // the long-lived broadcaster onto its publisher; if no, fall
        // back to the close-then-restart path the previous version
        // used uniformly.
        pumpJob =
            scope.launch {
                activeSpeaker.collectLatest { sp ->
                    if (sp == null || closed) return@collectLatest
                    // Wait until the underlying speaker is ready to
                    // broadcast (or has gone terminal). For a fresh
                    // session this resolves immediately because the
                    // wrapper's openOnce already saw Connected.
                    val ready =
                        sp.state.first { st ->
                            st is NestsSpeakerState.Connected ||
                                st is NestsSpeakerState.Broadcasting ||
                                st is NestsSpeakerState.Closed ||
                                st is NestsSpeakerState.Failed
                        }
                    if (ready !is NestsSpeakerState.Connected && ready !is NestsSpeakerState.Broadcasting) {
                        return@collectLatest
                    }
                    if (closed) return@collectLatest

                    val hotSwap = sp as? HotSwappablePublisherSource
                    if (hotSwap != null) {
                        runHotSwapIteration(hotSwap)
                    } else {
                        runLegacyIteration(sp)
                    }
                }
            }
    }

    /**
     * Hot-swap iteration body. On the first session: open a publisher,
     * construct the long-lived broadcaster, start it. On subsequent
     * sessions: open a publisher on the new session, swap into the
     * existing broadcaster, close the OLD publisher (FINs its
     * announce + group streams on the about-to-die session).
     *
     * Parks via [awaitCancellation] so [collectLatest] can cancel us
     * cleanly when the next session arrives. The broadcaster is NOT
     * stopped here — it lives across iterations and is only stopped
     * when the wrapper itself closes.
     */
    private suspend fun runHotSwapIteration(hotSwap: HotSwappablePublisherSource) {
        // Carry the previous session's audio-track group sequence
        // forward so kixelated/hang's `Container.Consumer.#run`
        // doesn't drop every post-recycle group as `sequence <
        // #active`. Read BEFORE opening the new publisher — there is
        // a tiny race window where the broadcaster could send one
        // more group on the old publisher between our read and the
        // swap-snapshot below; in practice that window is microseconds
        // (the broadcaster's swap is a single volatile write) and the
        // broadcaster's group cadence is 1 group/sec at the production
        // [NestMoqLiteBroadcaster.framesPerGroup], so the chance of a
        // duplicate sequence is negligible. Worst case the watcher
        // briefly stalls on one duplicate then catches up.
        val startSequence: Long = hotSwapBroadcaster?.currentPublisher?.nextSequence ?: 0L
        val newPublisher =
            try {
                hotSwap.openPublisherForHotSwap(
                    track = MoqLiteNestsListener.AUDIO_TRACK,
                    startSequence = startSequence,
                )
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Couldn't mint a publisher on this session (rare —
                // the session is already past Connected). Bail; the
                // next session swap will retry.
                return
            }
        if (closed) {
            runCatching { newPublisher.close() }
            return
        }

        val existing = hotSwapBroadcaster
        if (existing == null) {
            // First-ever iteration: build the broadcaster and start it.
            val broadcaster =
                NestMoqLiteBroadcaster(
                    capture = captureFactory(),
                    encoder = encoderFactory(),
                    initialPublisher = newPublisher,
                    scope = scope,
                    framesPerGroup = NestMoqLiteBroadcaster.DEFAULT_FRAMES_PER_GROUP,
                )
            try {
                broadcaster.start(
                    onTerminalFailure = {
                        // Bubble up via the speaker's state machine so
                        // the orchestrator's normal Failed-handling
                        // path recycles the session. Same shape the
                        // legacy `startBroadcasting`-internal path uses.
                        runCatching { hotSwap.reportBroadcastTerminalFailure() }
                    },
                    onLevel = onLevel,
                )
            } catch (t: Throwable) {
                runCatching { newPublisher.close() }
                throw t
            }
            // Apply current mute intent (a setMuted that arrived before
            // the broadcaster existed has already updated desiredMuted).
            if (desiredMuted) broadcaster.setMuted(true)
            hotSwapBroadcaster = broadcaster
        } else {
            // Subsequent iteration: hot swap. The capture / encoder
            // pipeline is already running and feeding [existing.publisher]
            // (the soon-to-be-old one). Install the new publisher,
            // grab the old, close it. The capture loop's next snapshot
            // picks up the new publisher and resets its group counter.
            val old = existing.swapPublisher(newPublisher)
            // Re-apply mute on the broadcaster — it survives swap, but
            // a setMuted that arrived during the gap between the last
            // session's terminal state and this swap may have flipped
            // [desiredMuted] without finding a live broadcaster (no, it
            // would have seen [hotSwapBroadcaster] since that's
            // long-lived; but the no-op on equality keeps this safe).
            existing.setMuted(desiredMuted)
            // Close the old publisher AFTER the broadcaster has the
            // new one. This is the order that matters: if we closed
            // first, the capture loop's next send would race the swap
            // and might see the closed publisher.
            if (old != null) runCatching { old.close() }
        }

        // The audio publisher is now live on this session. Flip the
        // underlying speaker's state machine into Broadcasting — the
        // hot-swap path bypasses `startBroadcasting`, so without this
        // the speaker (and the reconnect wrapper that mirrors it) would
        // sit on Connected forever. Done every iteration so a swapped
        // session — which arrives in Connected — re-enters Broadcasting.
        hotSwap.reportBroadcasting(desiredMuted)

        // Catalog track on the new session. Keeps the broadcast
        // discoverable to standards-aligned moq-lite watchers (the
        // kixelated/moq browser reference) across JWT refresh — without
        // this the catalog goes silent the moment the session recycles
        // and any watcher that attaches AFTER the recycle sees nothing
        // to subscribe to. Mirror of [MoqLiteNestsSpeaker.startBroadcasting]'s
        // catalog setup; same JSON, same emit-on-subscribe pattern.
        val catalogPayload =
            MoqLiteHangCatalog.opus48kJsonBytes(
                audioTrackName = MoqLiteNestsListener.AUDIO_TRACK,
                numberOfChannels = broadcastConfig.channelCount,
            )
        val priorCatalogPublisher = hotSwapCatalogPublisher
        val newCatalogPublisher =
            try {
                hotSwap.openPublisherForHotSwap(MoqLiteNestsListener.CATALOG_TRACK)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Couldn't mint a catalog publisher on this session.
                // Audio path is already live; treat as non-fatal — the
                // next session swap retries. Leave prior catalog state
                // alone (it's about to die with the prior session).
                null
            }
        if (newCatalogPublisher != null) {
            // Set the emit-on-subscribe hook BEFORE installing the
            // publisher reference, so the relay can't race a SUBSCRIBE
            // in between.
            newCatalogPublisher.setOnNewSubscriber {
                runCatchingCancellable {
                    newCatalogPublisher.send(catalogPayload)
                    newCatalogPublisher.endGroup()
                }
            }
            hotSwapCatalogPublisher = newCatalogPublisher
            // Tear down the prior session's catalog publisher AFTER the
            // new one is installed so the watcher only sees a brief
            // overlap rather than a gap. The prior publisher's session
            // is about to be torn down by the orchestrator anyway, but
            // graceful Announce(Ended) keeps the relay book-keeping
            // clean.
            if (priorCatalogPublisher != null) runCatching { priorCatalogPublisher.close() }
        }

        try {
            // Park until [collectLatest] cancels us on the next session
            // swap, OR [close] cancels [pumpJob]. The broadcaster keeps
            // running through the cancellation; only close() stops it.
            awaitCancellation()
        } finally {
            // Intentionally do NOT close the broadcaster or the catalog
            // publisher here. collectLatest cancels this iteration on
            // every session swap; closing now would force the catalog
            // republisher to drop a beat between sessions. The next
            // iteration handles teardown of the prior catalog after
            // installing its replacement (above), and [close] handles
            // teardown when the wrapper itself closes.
        }
    }

    /**
     * Legacy iteration body — used for IETF reference speakers and any
     * future [NestsSpeaker] that doesn't implement
     * [HotSwappablePublisherSource]. Identical to the pre-hot-swap
     * behaviour: open a fresh handle on each session, close it on
     * cancellation.
     */
    private suspend fun runLegacyIteration(sp: NestsSpeaker) {
        val handle =
            try {
                sp.startBroadcasting(onLevel)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            } ?: return
        if (closed) {
            runCatching { handle.close() }
            return
        }
        try {
            if (desiredMuted) {
                runCatchingCancellable { handle.setMuted(true) }
            }
            liveHandle.set(handle)
            awaitCancellation()
        } finally {
            if (liveHandle.get() === handle) liveHandle.set(null)
            runCatching { handle.close() }
        }
    }

    override suspend fun setMuted(muted: Boolean) {
        if (closed) return
        desiredMuted = muted
        // Apply to whichever live underlying exists. Hot-swap and
        // legacy paths are mutually exclusive in steady state — a
        // moq-lite session's wrapper never has a live legacy handle,
        // and vice versa — but both reads are cheap and harmless if
        // the unused one is null.
        hotSwapBroadcaster?.setMuted(muted)
        liveHandle.get()?.let { runCatchingCancellable { it.setMuted(muted) } }
    }

    override suspend fun close() {
        if (closed) return
        closed = true
        pumpJob?.cancel()
        // Tear down whichever path was active. For hot-swap, stopping
        // the broadcaster releases the mic + encoder AND closes the
        // current publisher (broadcaster.stop calls publisher.close
        // internally). For legacy, the per-session handle's close
        // releases its own broadcaster.
        //
        // Catalog gets explicit teardown because — unlike the audio
        // publisher which broadcaster.stop() closes for us — the
        // catalog publisher has no broadcaster wrapper. The
        // emit-on-subscribe hook itself doesn't need shutdown: it
        // only fires inside [PublisherStateImpl.registerInboundSubscription],
        // and closing the publisher prevents any further hook fires.
        hotSwapCatalogPublisher?.let { runCatching { it.close() } }
        hotSwapCatalogPublisher = null
        hotSwapBroadcaster?.let { runCatching { it.stop() } }
        hotSwapBroadcaster = null
        liveHandle.getAndSet(null)?.let { runCatching { it.close() } }
        onClose(this)
    }
}
