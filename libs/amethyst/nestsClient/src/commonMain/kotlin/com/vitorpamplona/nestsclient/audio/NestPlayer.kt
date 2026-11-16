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

import com.vitorpamplona.nestsclient.moq.MoqObject
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Bridges a track's `Flow<MoqObject>` (from [com.vitorpamplona.nestsclient.moq.SubscribeHandle.objects])
 * through an [OpusDecoder] into an [AudioPlayer].
 *
 * Single-track. To play multiple speakers in a room, instantiate one
 * [NestPlayer] per [com.vitorpamplona.nestsclient.moq.SubscribeHandle];
 * each owns its own decoder (Opus state is per-track).
 *
 * Lifecycle:
 *   - [play] starts the player and the decode loop. Returns immediately.
 *   - [stop] cancels the decode loop, stops the player, and releases the
 *     decoder. Idempotent.
 */
class NestPlayer(
    initialDecoder: OpusDecoder,
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
    /**
     * Number of decoded PCM frames to buffer before starting the underlying
     * [AudioPlayer]. Without pre-roll, the device begins consuming audio the
     * instant the first frame arrives and underruns at the first decode that
     * misses its 20 ms cadence — the most common cause of perceptible
     * dropouts on devices where Compose recomposition or GC briefly stalls
     * the decode loop.
     *
     * Production callers typically pass `5` (≈ 100 ms) — long enough to mask
     * a typical Main-thread hiccup, short enough that listeners don't notice
     * the join latency. Tests pass `0` to preserve the synchronous test
     * scheduler behaviour the existing assertions rely on.
     *
     * Default is `0` so existing tests stand without modification.
     */
    private val prerollFrames: Int = 0,
    /**
     * Optional factory called on every detected publisher boundary
     * (track-alias change in the inbound [MoqObject] stream) to mint a
     * fresh [OpusDecoder]. Used by the listener wrapper's re-issuing
     * subscription pump
     * ([com.vitorpamplona.nestsclient.ReconnectingNestsListener.reissuingSubscribe]):
     * each new SUBSCRIBE through the relay produces objects with a
     * different `trackAlias`, but they're spliced into the same
     * `SharedFlow` — without a decoder reset on the boundary, Opus's
     * predictor state from the prior publisher's last frame is fed
     * into the new publisher's first frame, producing an audible
     * warble at every JWT-refresh hot-swap on the speaker side OR
     * cliff-detector recycle on the listener side.
     *
     * Default `null` keeps the legacy behaviour (no boundary
     * detection, decoder lives for the player's whole lifetime) so
     * existing tests / callers that don't care about boundaries
     * stand unchanged. Production callers in
     * [com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel.openSubscription]
     * pass a closure that captures the per-subscription channel-count
     * and rebuilds via `decoderFactory(channelCount)`.
     */
    private val decoderFactory: (() -> OpusDecoder)? = null,
) {
    init {
        require(prerollFrames >= 0) { "prerollFrames must be >= 0, got $prerollFrames" }
    }

    /**
     * Active decoder. Replaced on detected publisher boundary when
     * [decoderFactory] is non-null. `var` so the boundary path can
     * release + rebuild without changing the rest of the loop's
     * decoder reference; the `private` confines mutation to this
     * class, and the decode loop runs single-coroutine so no cross-
     * thread visibility hazards.
     */
    private var decoder: OpusDecoder = initialDecoder

    private var job: Job? = null
    private var stopped = false

    /**
     * Start consuming [objects] in the background. Each MoQ object's payload
     * is fed to the Opus decoder; the resulting PCM frame is enqueued to the
     * player.
     *
     * Decoder errors are reported via [onError] but do NOT stop the loop —
     * one bad packet shouldn't tear down the room. Player errors are fatal.
     *
     * [onLevel] receives the peak amplitude of each successfully decoded
     * frame, normalized to `[0, 1]`. Default no-op so callers that don't
     * care about levels (tests, audio-only consumers) pay zero cost.
     * Invoked on the same dispatcher as the decode loop — typically
     * `viewModelScope`'s Main, so the consumer must keep its handler
     * lightweight (e.g. a HashMap put followed by a coalesced StateFlow
     * emission).
     */
    fun play(
        objects: Flow<MoqObject>,
        onError: (AudioException) -> Unit = { /* swallow */ },
        onLevel: (Float) -> Unit = { /* no-op */ },
    ) {
        check(!stopped) { "NestPlayer already stopped" }
        check(job == null) { "NestPlayer.play already called" }

        // Allocate the device synchronously so a [AudioException.DeviceUnavailable]
        // (audio policy denial, AudioTrack rejected, etc.) propagates to
        // the caller — typically `NestViewModel.openSubscription`, which
        // catches and rolls back the freshly-reserved subscription slot.
        // Routing this failure through `onError` instead would attach the
        // slot first and leave a permanent "Connecting…" spinner on the
        // speaker tile when the device fails to allocate.
        //
        // Two-phase startup: [AudioPlayer.start] allocates without
        // beginning playback; [AudioPlayer.beginPlayback] flips the
        // device into the playing state. We delay [beginPlayback] until
        // the pre-roll buffer is full so the first frames already
        // populate the device's internal buffer when the hardware
        // starts pulling samples.
        player.start()

        // Pre-roll buffer holds decoded PCM until either [prerollFrames]
        // frames have arrived or the upstream flow ends. Once the
        // threshold is met (or the flow ends with anything queued), we
        // call [AudioPlayer.beginPlayback] and flush the buffer in a
        // tight loop so the device starts playback with a populated
        // buffer. A flow that never produces PCM (decoder always empty,
        // no audio in the room) never calls [beginPlayback] — the
        // allocated device sits in the "ready, not playing" state until
        // [stop] tears it down.
        job =
            scope.launch {
                val preroll = ArrayDeque<ShortArray>(prerollFrames.coerceAtLeast(1))
                var playbackBegun = false
                // Diagnostic counters: at 50 fps a per-frame log floods logcat;
                // throttle to every Nth event.
                var receivedObjects: Long = 0L
                var decodedFrames: Long = 0L
                var emptyDecodes: Long = 0L
                var enqueued: Long = 0L
                Log.d("NestPlay") {
                    "NestPlayer.play started (prerollFrames=$prerollFrames)"
                }

                suspend fun beginAndFlushIfNeeded() {
                    if (playbackBegun) return
                    if (preroll.isEmpty()) return
                    // Flush BEFORE [beginPlayback] so the device starts
                    // playing against an already-populated buffer rather
                    // than emitting silence for the microseconds it
                    // takes the flush loop to fill. AudioTrack
                    // MODE_STREAM explicitly supports write() before
                    // play() per the Android docs — that's the
                    // textbook pre-roll pattern. Order matters
                    // because [beginPlayback] is the moment the
                    // hardware starts pulling samples; getting
                    // [enqueue] in first means the very first sample
                    // pulled is from our pre-rolled audio, not silence.
                    Log.d("NestPlay") {
                        "NestPlayer flushing preroll (${preroll.size} frames) → beginPlayback"
                    }
                    while (preroll.isNotEmpty()) {
                        player.enqueue(preroll.removeFirst())
                    }
                    player.beginPlayback()
                    playbackBegun = true
                    Log
                        .d("NestPlay") { "NestPlayer beginPlayback returned" }
                }
                // Track-alias of the most recently observed object.
                // A change signals a publisher boundary (re-issuing
                // subscription wrapper spliced in a new SUBSCRIBE).
                // Only consulted when [decoderFactory] is non-null;
                // legacy callers without a factory keep the prior
                // single-decoder behaviour.
                var lastTrackAlias: Long? = null
                try {
                    objects.collect { obj ->
                        receivedObjects += 1
                        if (receivedObjects % PLAY_LOG_THROTTLE == 0L) {
                            Log.d("NestPlay") {
                                "NestPlayer received obj #$receivedObjects (decoded=$decodedFrames empty=$emptyDecodes enqueued=$enqueued playbackBegun=$playbackBegun)"
                            }
                        }
                        // Publisher-boundary detection: if the trackAlias
                        // changed since the last object AND we have a
                        // factory to mint a fresh decoder, release the
                        // current decoder + build a new one. Without
                        // this, Opus's predictor state from the prior
                        // publisher's last frame is fed into the new
                        // publisher's first frame, producing audible
                        // warble at every JWT-refresh hot-swap (speaker
                        // side) or cliff-detector recycle (listener side).
                        // The prior-trackAlias guard avoids a spurious
                        // rebuild on the very first frame.
                        //
                        // Build the replacement BEFORE releasing the old
                        // decoder so a factory failure (e.g. MediaCodec
                        // contention, audio policy denial mid-session)
                        // doesn't leave the field referencing a
                        // released codec — every subsequent decode
                        // would then throw `IllegalStateException` with
                        // no recovery path. On factory failure, log
                        // and keep using the existing decoder; cross-
                        // publisher predictor state is wrong but at
                        // least audio keeps playing.
                        val factory = decoderFactory
                        if (factory != null && lastTrackAlias != null && obj.trackAlias != lastTrackAlias) {
                            val replacement =
                                runCatching { factory() }
                                    .onFailure { t ->
                                        if (t is CancellationException) throw t
                                        Log.w("NestPlay") {
                                            "NestPlayer decoder factory threw on trackAlias " +
                                                "$lastTrackAlias → ${obj.trackAlias}; keeping old decoder " +
                                                "(${t::class.simpleName}: ${t.message})"
                                        }
                                    }.getOrNull()
                            if (replacement != null) {
                                Log.d("NestPlay") {
                                    "NestPlayer publisher boundary: trackAlias $lastTrackAlias → ${obj.trackAlias}; rebuilding decoder"
                                }
                                runCatching { decoder.release() }
                                decoder = replacement
                            }
                        }
                        lastTrackAlias = obj.trackAlias
                        val pcm =
                            try {
                                decoder.decode(obj.payload)
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (t: Throwable) {
                                Log.w("NestPlay") {
                                    "decoder.decode threw on obj #$receivedObjects: ${t::class.simpleName}: ${t.message}"
                                }
                                onError(
                                    AudioException(
                                        AudioException.Kind.DecoderError,
                                        "Opus decode failed for object ${obj.objectId}",
                                        t,
                                    ),
                                )
                                return@collect
                            }
                        if (pcm.isEmpty()) {
                            emptyDecodes += 1
                            if (emptyDecodes % PLAY_LOG_THROTTLE == 0L) {
                                Log.w("NestPlay") {
                                    "decoder returned empty pcm (count=$emptyDecodes / received=$receivedObjects)"
                                }
                            }
                        } else {
                            decodedFrames += 1
                            onLevel(peakAmplitude(pcm))
                            if (playbackBegun) {
                                val enqueueStart = System.currentTimeMillis()
                                player.enqueue(pcm)
                                val enqueueMs = System.currentTimeMillis() - enqueueStart
                                enqueued += 1
                                if (enqueued % PLAY_LOG_THROTTLE == 0L || enqueueMs > 50) {
                                    Log.d("NestPlay") {
                                        "NestPlayer enqueued #$enqueued (took ${enqueueMs}ms)"
                                    }
                                }
                            } else {
                                preroll.addLast(pcm)
                                if (preroll.size >= prerollFrames) {
                                    beginAndFlushIfNeeded()
                                }
                            }
                        }
                    }
                    Log.w("NestPlay") {
                        "NestPlayer objects flow COMPLETED (received=$receivedObjects decoded=$decodedFrames empty=$emptyDecodes enqueued=$enqueued)"
                    }
                    // Flow ended without enough frames to fill the pre-roll
                    // (e.g. the publisher cycled before pre-roll was full,
                    // or the room ended). Flush whatever's queued so any
                    // already-decoded audio still reaches the device.
                    beginAndFlushIfNeeded()
                } catch (ce: CancellationException) {
                    Log.d("NestPlay") {
                        "NestPlayer cancelled (received=$receivedObjects decoded=$decodedFrames enqueued=$enqueued)"
                    }
                    throw ce
                } catch (t: Throwable) {
                    Log.w("NestPlay") {
                        "NestPlayer pipeline threw: ${t::class.simpleName}: ${t.message}"
                    }
                    onError(
                        AudioException(
                            AudioException.Kind.PlaybackFailed,
                            "audio pipeline failed",
                            t,
                        ),
                    )
                }
            }
    }

    /**
     * Stop playback, cancel the decode loop, release the decoder. Idempotent.
     *
     * Suspending so callers can await the loop's exit before releasing
     * native resources. Calling `decoder.release()` while another coroutine
     * is mid-`decoder.decode(...)` is undefined behaviour for MediaCodec
     * (and most native decoders); `cancelAndJoin` waits for the loop to
     * unwind through its CancellationException path before we proceed.
     */
    suspend fun stop() {
        if (stopped) return
        stopped = true
        Log
            .d("NestPlay") { "NestPlayer.stop()" }
        job?.cancelAndJoin()
        runCatching { player.stop() }
        runCatching { decoder.release() }
    }

    companion object {
        // Diagnostic throttle: log every Nth event during normal flow so a
        // sustained 50 fps stream doesn't flood logcat.
        private const val PLAY_LOG_THROTTLE: Long = 50L
    }
}
