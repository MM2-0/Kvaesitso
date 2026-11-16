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
package com.vitorpamplona.nestsclient.audio

import com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import com.vitorpamplona.quic.Varint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * Mirror of [NestBroadcaster] but driving a moq-lite
 * [MoqLitePublisherHandle] instead of an IETF `MoqSession.TrackPublisher`.
 * Keeps the IETF broadcaster intact for the IETF unit-test suite while
 * letting the production speaker path use moq-lite.
 *
 * Lifecycle and audit comments mirror the IETF version 1:1 — only the
 * sink type changes.
 */
class NestMoqLiteBroadcaster(
    private val capture: AudioCapture,
    private val encoder: OpusEncoder,
    initialPublisher: MoqLitePublisherHandle,
    private val scope: CoroutineScope,
    /**
     * Number of consecutive Opus frames to pack into a single moq-lite
     * group (= one QUIC unidirectional stream). The first call to
     * [MoqLitePublisherHandle.send] in each group opens the stream;
     * subsequent calls within the same group write additional
     * varint-prefixed frames; [MoqLitePublisherHandle.endGroup] FINs
     * the stream and the next [send] starts a fresh group.
     *
     * **Default = 5 (≈ 100 ms of audio per group → 10 streams/sec).**
     *
     * The story:
     *
     * Round 1 found that the production relay's lifetime peer-uni
     * stream cap of 100 was bitten by our `:quic` never emitting
     * `MAX_STREAMS_UNI` extensions; that fix landed and short
     * broadcasts (≤ 100 frames) now deliver 100/100.
     *
     * Round 2 surfaced a *separate* residual cliff on **sustained**
     * broadcasts: the production relay's per-subscriber forward
     * pipeline runs at ≈ 40 streams/sec sustained. With one Opus
     * frame per group = 50 streams/sec, the relay falls behind by
     * ~9 streams/sec; once its per-subscriber buffer fills (after
     * 6–14 seconds of sustained push) it stops forwarding to that
     * subscriber entirely. Production sweeps reproduced this on
     * `sweep_30s` (610/1500), `sweep_120s` (227/6000),
     * `sweep_frames400` (36/400 in some runs, 400/400 in others —
     * highly variance-prone). Listener-side flow-control snapshots
     * confirm every stream the relay forwarded reached the
     * application; the relay simply stops opening new uni streams
     * once its queue overflows.
     *
     * Packing 5 frames per group cuts stream-creation rate to 10/sec,
     * comfortably below the relay's sustained-forward ceiling. Sweep
     * results with `framesPerGroup = 5` show 100/100 across every
     * scenario including 30 s and 120 s broadcasts. Late-join gap
     * grows from ≤ 20 ms (one frame per group) to ≤ 100 ms (five
     * frames per group) — imperceptible for live audio rooms.
     *
     * See `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`.
     *
     * Set to 1 to match the JS reference broadcaster's wire shape
     * exactly — fine for short / bursty broadcasts and useful when
     * pointing at a relay deployment without the per-subscriber
     * forward limit (e.g. self-hosted moq-rs with tuned config).
     */
    private val framesPerGroup: Int = DEFAULT_FRAMES_PER_GROUP,
) {
    init {
        require(framesPerGroup >= 1) { "framesPerGroup must be >= 1, got $framesPerGroup" }
    }

    private var job: Job? = null

    @Volatile private var stopped: Boolean = false

    @Volatile private var muted: Boolean = false

    /**
     * Active publisher the capture loop pushes frames into. Mutable +
     * `@Volatile` so [swapPublisher] can atomically retarget the loop
     * onto a fresh moq-lite session's publisher when the
     * [com.vitorpamplona.nestsclient.connectReconnectingNestsSpeaker]
     * orchestrator recycles the WebTransport session for a JWT
     * refresh — without restarting capture or the encoder. The
     * capture loop snapshots this reference once per frame, so a swap
     * takes effect on the very next frame and any in-flight send on
     * the previous publisher is allowed to complete (or fail
     * gracefully, caught by `runCatching`).
     *
     * Per-instance group sequence: each publisher restarts at sequence
     * 0, since the publisher state lives inside [MoqLitePublisherHandle]
     * (one per moq-lite session). That's fine — listeners use group
     * sequence for ordering within a single subscription's uni-stream-
     * per-group flow, not across publisher cycles.
     */
    @Volatile private var publisher: MoqLitePublisherHandle = initialPublisher

    /**
     * Volatile read of the broadcaster's current publisher reference,
     * for callers (typically the hot-swap pump in
     * [com.vitorpamplona.nestsclient.connectReconnectingNestsSpeaker])
     * that want to read its [MoqLitePublisherHandle.nextSequence]
     * before swapping in a fresh publisher. Don't use this to send
     * — that contract belongs to the broadcaster's send loop and
     * the caller would race the loop's swap-snapshot.
     */
    val currentPublisher: MoqLitePublisherHandle
        get() = publisher

    /**
     * Start capturing + encoding + publishing in the background.
     * Returns immediately. Calling twice is an error. If
     * [AudioCapture.start] throws, the broadcaster is left stopped and
     * the exception propagates.
     *
     * [onTerminalFailure] fires exactly once when the loop bails after
     * [MAX_CONSECUTIVE_SEND_ERRORS] consecutive `publisher.send`
     * failures. The broadcaster has stopped capturing by the time this
     * callback runs; the caller (typically [MoqLiteNestsSpeaker]) is
     * expected to mark the speaker `Failed` so the reconnect
     * orchestrator can recycle the session — without this signal the
     * outward speaker state stays stuck on `Broadcasting` and the
     * orchestrator never knows to act.
     */
    fun start(
        onTerminalFailure: () -> Unit = { /* swallow */ },
        onError: (AudioException) -> Unit = { /* swallow */ },
        /**
         * Fires once per Opus frame that successfully reaches
         * [MoqLitePublisherHandle.send] — i.e. the frame is on the
         * wire from this client's perspective. The argument is the
         * peak amplitude of the underlying PCM frame, normalized to
         * `[0, 1]` (same scale [NestPlayer] reports for remote
         * speakers).
         *
         * This deliberately taps **after** the mute gate and **after**
         * a successful send, so the local "I'm speaking" ring on the
         * UI tracks ground truth (frames actually leaving) rather
         * than any UI-side mute / role state that could lie.
         *
         * Default no-op so callers that don't render a local ring
         * (tests, headless interop) pay zero cost.
         */
        onLevel: (Float) -> Unit = { /* no-op */ },
    ) {
        check(!stopped) { "NestMoqLiteBroadcaster already stopped" }
        check(job == null) { "NestMoqLiteBroadcaster.start already called" }

        try {
            capture.start()
        } catch (t: Throwable) {
            stopped = true
            runCatching { capture.stop() }
            throw t
        }
        job =
            scope.launch {
                // Counts frames written to the current moq-lite group.
                // Reset to 0 immediately after each [endGroup] so the
                // next [send] auto-starts a fresh group via
                // [MoqLitePublisherHandle.send]'s "open on first frame"
                // contract.
                var framesInCurrentGroup = 0
                // Consecutive publisher.send / endGroup throw count. A
                // permanently-dead transport (session closed under us,
                // every openUniStream rejected) keeps producing errors at
                // capture cadence; without a guard the broadcaster would
                // hold the mic open forever, drain battery, and spam
                // onError. After [MAX_CONSECUTIVE_SEND_ERRORS] failures
                // we bail. publisher.send returning `false` (no inbound
                // subscriber) is NOT counted — empty rooms are normal.
                var consecutiveSendErrors = 0
                // Diagnostic counters: throttled logging at 50Hz capture rate
                // would flood logcat without these.
                var sentFrames: Long = 0L
                var droppedNoSubFrames: Long = 0L
                // Track which publisher we last sent to. On swapPublisher
                // (JWT-refresh hot swap), the snapshot below picks up the
                // new reference; we reset framesInCurrentGroup so the
                // first frame on the new publisher starts a fresh group
                // (the new publisher's group sequence is 0 anyway). Without
                // this, a mid-group swap would feed the new publisher
                // frames as if they continued the old publisher's group,
                // and the relay would see two unrelated uni streams under
                // the same logical group.
                var lastPublisher: MoqLitePublisherHandle = publisher
                // kixelated/moq `hang` "legacy" container wire format:
                // every frame inside a moq-lite group is
                //   varint(timestamp_us) + raw_codec_payload
                // (`rs/hang/src/container/frame.rs`,
                // Timescale<1_000_000>). Watchers that read our
                // catalog's `container.kind = "legacy"` declaration
                // will skip the leading varint as a microsecond
                // timestamp; they MUST receive a real timestamp or
                // their decoder picks up garbage bytes ahead of the
                // Opus packet.
                //
                // Frame-index-derived timestamp (NOT wall clock). Each
                // captured PCM frame occupies exactly one
                // [AudioFormat.FRAME_DURATION_US] slot in the timeline,
                // and the timestamp is `nextFrameIndex *
                // FRAME_DURATION_US` snapped to that grid. This matters
                // because:
                //   - `current.send(...)` suspends on transport
                //     backpressure; a wall-clock timestamp captured at
                //     send-time would drift forward of the actual
                //     capture time of the audio sample, and the watcher's
                //     WebCodecs AudioDecoder would schedule playback
                //     at the wrong instant (audible offset between
                //     speaker and listener).
                //   - The capture loop is wall-clock-pinned anyway —
                //     `capture.readFrame()` blocks until the next 20 ms
                //     of PCM is ready — so frame-index advances at the
                //     same rate as wall time across the whole
                //     broadcast lifetime, with no codec drift relative
                //     to the watcher's playback clock.
                //
                // The counter advances on EVERY captured PCM frame —
                // including encoder failures, empty-encoded frames, and
                // muted frames — so a mute gap shows up on the wire as
                // a real wall-clock gap (timestamps jump forward by the
                // muted duration) rather than collapsing the silence.
                // Survives publisher hot-swap (single-broadcast,
                // single-encoder; the new publisher inherits the
                // running counter) for the same reason the old
                // wall-clock TimeMark did: timestamps are codec-payload
                // metadata, not stream-position.
                var nextFrameIndex = 0L
                // Mute-edge tracking. On the unmuted→muted transition we
                // FIN the open uni stream so the watcher sees a clean
                // group boundary instead of a half-delivered group that
                // never completes — kixelated/hang's
                // `Container.Consumer.#runGroup` parks on
                // `await group.consumer.readFrame()` until either a
                // frame arrives or the group hits FIN, and renders a
                // "stalled" indicator while it waits. Without the FIN,
                // muting Amethyst lights up that indicator on every web
                // watcher even though the broadcast is intentionally
                // silent. The FIN clears it; unmuting opens a fresh
                // group cleanly via the standard
                // `currentGroup ?: openNextGroupLocked()` path inside
                // `PublisherStateImpl.send`.
                var wasMuted = false
                try {
                    while (true) {
                        val pcm = capture.readFrame() ?: break
                        val timestampUs = nextFrameIndex * AudioFormat.FRAME_DURATION_US
                        nextFrameIndex += 1
                        val opus =
                            try {
                                encoder.encode(pcm)
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (t: Throwable) {
                                onError(
                                    AudioException(
                                        AudioException.Kind.EncoderError,
                                        "Opus encode failed for a frame",
                                        t,
                                    ),
                                )
                                continue
                            }
                        if (opus.isEmpty()) continue
                        if (muted) {
                            // Unmuted → muted edge: FIN the current
                            // group's uni stream once. Doesn't reset
                            // [framesInCurrentGroup] since
                            // `endGroup` is a no-op when no group is
                            // open, and the next post-unmute send will
                            // open a fresh group anyway. `runCatching`
                            // because a transport-side close during
                            // mute is non-fatal — the broadcaster's
                            // terminal-failure path picks up real
                            // breakage on the next live frame.
                            if (!wasMuted) {
                                wasMuted = true
                                runCatchingCancellable { publisher.endGroup() }
                                framesInCurrentGroup = 0
                            }
                            continue
                        }
                        // Muted → unmuted edge: clear the latch so the
                        // next mute transition fires endGroup again.
                        wasMuted = false
                        // Snapshot the publisher reference once per frame.
                        // If [swapPublisher] mid-loop installed a new
                        // reference, pick it up here and reset the group
                        // counter so the new publisher's first frame
                        // starts a clean group. Frames already in flight
                        // on the previous publisher are allowed to
                        // complete (or fail gracefully on `runCatching`).
                        val current = publisher
                        if (current !== lastPublisher) {
                            framesInCurrentGroup = 0
                            lastPublisher = current
                        }
                        // Pack [framesPerGroup] consecutive Opus frames
                        // into the same moq-lite group / QUIC uni stream
                        // before FINning. See the [framesPerGroup] kdoc
                        // for the production cliff this works around.
                        val sendOutcome =
                            runCatching {
                                // Single-allocation framing: write the
                                // timestamp varint directly into a buffer
                                // sized for `varint + opus`, then copy
                                // the opus bytes after it. The earlier
                                // shape (`Varint.encode(...) + opus`)
                                // allocated twice per frame — once for
                                // the varint ByteArray, once for the
                                // concatenated payload. At 50 fps × N
                                // speakers this is measurable young-gen
                                // pressure on the audio hot path; the
                                // mirror optimisation already lives in
                                // `PublisherStateImpl.send` for the
                                // outer size-prefix wrap.
                                val tsLen = Varint.size(timestampUs)
                                val payload = ByteArray(tsLen + opus.size)
                                Varint.writeTo(timestampUs, payload, 0)
                                opus.copyInto(payload, tsLen)
                                val accepted = current.send(payload)
                                framesInCurrentGroup += 1
                                if (framesInCurrentGroup >= framesPerGroup) {
                                    current.endGroup()
                                    framesInCurrentGroup = 0
                                }
                                accepted
                            }
                        sendOutcome
                            .onSuccess { accepted ->
                                consecutiveSendErrors = 0
                                if (accepted) {
                                    sentFrames += 1
                                    if (sentFrames % SEND_LOG_THROTTLE == 0L) {
                                        Log.d("NestTx") {
                                            "broadcaster sent frame #$sentFrames (group $framesInCurrentGroup/$framesPerGroup)"
                                        }
                                    }
                                    // Only tap the speaking-ring on a frame
                                    // that actually reached an inbound
                                    // subscriber. Without this gate the local
                                    // ring lights up during the pre-subscribe
                                    // window AND after a relay-side cliff —
                                    // the speaker would believe they're being
                                    // heard when no audio is on the wire.
                                    onLevel(peakAmplitude(pcm))
                                } else {
                                    droppedNoSubFrames += 1
                                    if (droppedNoSubFrames % SEND_LOG_THROTTLE == 0L) {
                                        Log.w("NestTx") {
                                            "broadcaster send returned false — frame dropped (count=$droppedNoSubFrames, sent=$sentFrames)"
                                        }
                                    }
                                }
                            }.onFailure { t ->
                                if (t is CancellationException) throw t
                                consecutiveSendErrors += 1
                                Log.w("NestTx") {
                                    "broadcaster send threw (consecutive=$consecutiveSendErrors): ${t::class.simpleName}: ${t.message}"
                                }
                                onError(
                                    AudioException(
                                        AudioException.Kind.PlaybackFailed,
                                        "publisher.send failed",
                                        t,
                                    ),
                                )
                                if (consecutiveSendErrors >= MAX_CONSECUTIVE_SEND_ERRORS) {
                                    onError(
                                        AudioException(
                                            AudioException.Kind.PlaybackFailed,
                                            "broadcast pipeline gave up after " +
                                                "$consecutiveSendErrors consecutive send failures",
                                            t,
                                        ),
                                    )
                                    // Surface the bail so the speaker
                                    // can flip to Failed and the
                                    // reconnect orchestrator recycles
                                    // the session. Caller still owns
                                    // [stop] — we don't release the mic
                                    // ourselves to avoid double-stop
                                    // races with a concurrent caller.
                                    runCatching { onTerminalFailure() }
                                    return@launch
                                }
                            }
                    }
                    // EOF on the capture side. Flush whatever's in the
                    // open group on the most-recently-used publisher so
                    // the relay sees its FIN and the listener doesn't
                    // sit on a half-delivered group buffer. Use
                    // [lastPublisher] (not the live [publisher] field)
                    // because a swap-then-EOF would otherwise FIN a
                    // group on a publisher that didn't open it.
                    if (framesInCurrentGroup > 0) {
                        runCatching { lastPublisher.endGroup() }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    onError(
                        AudioException(
                            AudioException.Kind.PlaybackFailed,
                            "audio capture pipeline failed",
                            t,
                        ),
                    )
                }
            }
    }

    /**
     * Toggle whether captured frames reach the wire. Mic stays open and
     * encoder keeps state consistent so unmute is sample-accurate.
     */
    fun setMuted(muted: Boolean) {
        if (stopped) return
        this.muted = muted
    }

    /**
     * Hot-swap the underlying publisher. Used by
     * [com.vitorpamplona.nestsclient.connectReconnectingNestsSpeaker]'s
     * orchestrator to retarget the broadcaster onto a fresh moq-lite
     * session's publisher when the JWT-refresh window fires — without
     * tearing down the AudioRecord / Opus encoder. Returns the previous
     * publisher reference so the caller can close it after the new one
     * is wired up.
     *
     * **Closing the returned old publisher** is the caller's
     * responsibility — this method only swaps the reference so the
     * capture loop's next snapshot picks up the new publisher. The
     * caller typically does:
     *
     *   1. Open a new moq-lite session, mint a new publisher on it.
     *   2. Call [swapPublisher] with the new publisher; cache the old.
     *   3. Close the old publisher (FINs the announce bidi + current
     *      group's uni stream on the old session).
     *   4. Close the old moq-lite session (drops the WebTransport).
     *
     * No-op if the broadcaster is already [stop]ped (returns null) — the
     * capture loop is already gone, so swapping in a new publisher would
     * just leak it.
     */
    fun swapPublisher(newPublisher: MoqLitePublisherHandle): MoqLitePublisherHandle? {
        if (stopped) return null
        val old = publisher
        if (old === newPublisher) return null
        publisher = newPublisher
        return old
    }

    /**
     * Stop the loop, release the mic, release the encoder, close the
     * moq-lite publisher (which sends `Announce(Ended)` on every active
     * announce bidi). Idempotent. We `cancelAndJoin` the loop before
     * releasing the encoder + publisher so the loop's last
     * `encoder.encode` / `publisher.send` can't race the close path.
     */
    suspend fun stop() {
        if (stopped) return
        stopped = true
        job?.cancelAndJoin()
        runCatching { capture.stop() }
        runCatching { encoder.release() }
        runCatching { publisher.close() }
    }

    companion object {
        /**
         * Default moq-lite group size = 50 Opus frames ≈ 1 s of audio.
         *
         * The relay-side cliff this defends against is per-subscriber
         * forward-stream-rate, not per-frame or per-byte: moq-rs
         * starts FIN'ing the per-subscriber publisher pipe once its
         * per-subscriber uni-stream creation queue can't drain fast
         * enough. Two-phone production tests on
         * `claude/fix-nests-audio-receiver-HCgOY` showed:
         *
         *   - `framesPerGroup = 10` (5 streams/sec) → cliff after
         *     ~16 s of streaming, ~6 s of audio lost at the tail
         *     (commit 6e4df4a logcat, run 18:37:43..18:38:08)
         *   - `framesPerGroup = 5`  (10 streams/sec) → cliff after
         *     ~13 s of streaming, same dropout shape (commit
         *     d6517cf logcat run 14:25 — original bug report).
         *
         * 50 frames/group → 1 stream/sec at the production 50 fps
         * Opus cadence. The relay's per-subscriber forward queue
         * has measurable headroom at 1 stream/sec — sweep tests
         * (`fpg-all` = 100 frames in a single group) consistently
         * deliver 100/100 in production, and `fpg20` similarly
         * passes. We pick 50 (not 100) because:
         *
         *   - Late-join gap is bounded by group size: a listener
         *     joining mid-broadcast has to wait until the next
         *     group boundary for the first frame. 50 frames = 1 s
         *     gap, which matches what the existing
         *     `ROOM_PLAYER_PREROLL_FRAMES = 10` audio-buffer + the
         *     ~250 ms AudioTrack jitter buffer was already designed
         *     to mask. 100 frames (2 s) is past that.
         *   - QUIC stream-level reliability: each group is one uni
         *     stream, and a stream RST loses the whole group. 1 s
         *     of audio loss on RST is bearable; 2 s is noticeably
         *     a "skip".
         *   - Sender-side encode latency stays at 1 s — no worse
         *     than the natural buffering audio rooms already do.
         *
         * Pair with the listener-side cliff-detector in
         * `NestViewModel` as belt-and-suspenders: if 50 frames/group
         * STILL hits a cliff (relay version drift, network
         * congestion, etc.) the detector recycles the transport so
         * the next session reopens the relay's queue.
         *
         * Late-join gap: ≤ 1 s (one group boundary) — within the
         * existing audio-room buffering envelope.
         */
        const val DEFAULT_FRAMES_PER_GROUP: Int = 50

        /**
         * Maximum consecutive [MoqLitePublisherHandle.send] / [endGroup]
         * exceptions before the broadcaster bails. At 50 fps, 250 frames
         * is ≈ 5 s of solid failures — far longer than any transient
         * relay hiccup, short enough to stop draining the mic when the
         * transport is irrecoverably dead.
         */
        const val MAX_CONSECUTIVE_SEND_ERRORS: Int = 250

        // Diagnostic: log every Nth frame (sent or dropped) so a sustained
        // window doesn't flood logcat at 50 fps.
        private const val SEND_LOG_THROTTLE: Long = 50L
    }
}
