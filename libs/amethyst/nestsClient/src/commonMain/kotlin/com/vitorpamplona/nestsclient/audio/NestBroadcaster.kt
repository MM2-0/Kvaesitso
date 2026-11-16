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

import com.vitorpamplona.nestsclient.moq.MoqSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * Inverse of [NestPlayer]: pulls PCM from an [AudioCapture], runs it
 * through an [OpusEncoder], and pushes the resulting Opus packets into a
 * [MoqSession.TrackPublisher] as MoQ OBJECT_DATAGRAMs.
 *
 * One instance per outgoing track (typically one per local speaker, since
 * Opus encoder state is per-stream).
 *
 * Lifecycle:
 *   - [start] opens the mic, starts the capture-encode-publish loop.
 *   - [setMuted] keeps the loop running but stops emitting frames so unmute
 *     is instant. Default unmuted.
 *   - [stop] cancels the loop, stops the mic, releases the encoder, and
 *     closes the publisher.
 *
 * Encoder warm-up frames (encoder returns empty) are dropped silently.
 * Encode failures are reported via [onError] but do NOT tear the loop down —
 * one bad frame shouldn't end the broadcast.
 */
class NestBroadcaster(
    private val capture: AudioCapture,
    private val encoder: OpusEncoder,
    private val publisher: MoqSession.TrackPublisher,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    @Volatile private var stopped: Boolean = false

    @Volatile private var muted: Boolean = false

    /**
     * Start capturing + encoding + publishing in the background. Returns
     * immediately. Calling twice is an error. If [AudioCapture.start]
     * throws (mic device unavailable, etc.), the broadcaster is left in
     * a stopped state and the exception propagates so the caller can
     * surface it to the user.
     */
    fun start(
        /**
         * See [NestMoqLiteBroadcaster.start]'s `onTerminalFailure` kdoc —
         * fires once when the loop bails after MAX_CONSECUTIVE_SEND_ERRORS
         * so the speaker can transition to Failed and the orchestrator
         * can recycle.
         */
        onTerminalFailure: () -> Unit = { /* swallow */ },
        onError: (AudioException) -> Unit = { /* swallow */ },
        /** See [NestMoqLiteBroadcaster.start]'s `onLevel` kdoc. */
        onLevel: (Float) -> Unit = { /* no-op */ },
    ) {
        check(!stopped) { "NestBroadcaster already stopped" }
        check(job == null) { "NestBroadcaster.start already called" }

        // Audit round-2 MoQ #7: capture.start() can throw before we have
        // a job. Mark stopped + propagate so a half-started capture isn't
        // left holding the mic and the broadcaster instance can't be
        // accidentally re-started.
        try {
            capture.start()
        } catch (t: Throwable) {
            stopped = true
            runCatching { capture.stop() }
            throw t
        }
        job =
            scope.launch {
                // Consecutive publisher.send error count — see the
                // moq-lite broadcaster's identical guard for rationale:
                // a permanently-dead transport spins the mic + encoder
                // forever without a threshold.
                var consecutiveSendErrors = 0
                try {
                    while (true) {
                        val pcm = capture.readFrame() ?: break
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
                        if (muted) continue
                        val sendOutcome = runCatching { publisher.send(opus) }
                        sendOutcome
                            .onSuccess {
                                consecutiveSendErrors = 0
                                onLevel(peakAmplitude(pcm))
                            }.onFailure { t ->
                                if (t is CancellationException) throw t
                                consecutiveSendErrors += 1
                                // Network drop on send is recoverable — log via onError but
                                // don't stop the loop; the next frame may go through.
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
                                    runCatching { onTerminalFailure() }
                                    return@launch
                                }
                            }
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
     * Toggle whether captured frames reach the wire. The mic stays open and
     * the encoder keeps state consistent so unmute resumes mid-conversation
     * with no glitch. No-op once [stop] has been called.
     */
    fun setMuted(muted: Boolean) {
        if (stopped) return
        this.muted = muted
    }

    /**
     * Stop the loop, release the mic, release the encoder, close the MoQ
     * publisher (which fires SUBSCRIBE_DONE to every attached subscriber).
     * Idempotent.
     *
     * Implementation note: we `cancelAndJoin` the loop before releasing
     * the encoder and closing the publisher. Otherwise the loop's last
     * `encoder.encode(...)` or `publisher.send(...)` could race
     * `encoder.release()` / `publisher.close()` and produce orphan
     * OBJECT_DATAGRAMs to subscribers that already received SUBSCRIBE_DONE,
     * or use-after-release on the native MediaCodec.
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
        /** See [NestMoqLiteBroadcaster.MAX_CONSECUTIVE_SEND_ERRORS]. */
        const val MAX_CONSECUTIVE_SEND_ERRORS: Int = 250
    }
}
