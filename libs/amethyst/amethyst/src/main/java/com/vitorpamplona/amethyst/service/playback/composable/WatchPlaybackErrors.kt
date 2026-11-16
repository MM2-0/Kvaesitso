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
package com.vitorpamplona.amethyst.service.playback.composable

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.vitorpamplona.amethyst.service.playback.PLAYBACK_DIAG_TAG
import com.vitorpamplona.amethyst.service.playback.composable.controls.getVideoTrackGroup
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.delay

// Debug tag for the playback-error lifecycle. Logs every appearance, clear, synthetic-stall raise
// and recovery so a transient decoder-init collision (which self-recovers on a later attempt) can
// be told apart from a genuinely undecodable stream in a field logcat. See WatchPlaybackErrors.
internal const val ERROR_LOG_TAG = "PlaybackError"

private fun playbackStateName(state: Int): String =
    when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }

// Summarizes the currently-selected video track (codec + resolution + mime). A change in this line
// across an #EXT-X-DISCONTINUITY is the signature of an ad-splice codec reconfig — a second, distinct
// failure mode from BEHIND_LIVE_WINDOW.
@OptIn(UnstableApi::class)
private fun Tracks.selectedVideoSummary(): String {
    val group = getVideoTrackGroup(this) ?: return "none"
    for (i in 0 until group.length) {
        if (group.isTrackSelected(i)) {
            val f = group.getTrackFormat(i)
            return "codec=${f.codecs} ${f.width}x${f.height} mime=${f.sampleMimeType} fps=${f.frameRate}"
        }
    }
    return "none"
}

/**
 * Flattens a [PlaybackException] into a single line: error code, message, and the full nested
 * cause chain (e.g. `DecoderInitializationException <- MediaCodec.CodecException`). The cause
 * chain is what distinguishes "format truly unsupported" from "decoder failed to start while a
 * second controller held the codec" — both surface as the same top-level renderer error with
 * `format_supported=YES`.
 *
 * Internal (not private) so [GetVideoController] can log the same detail at controller-acquire
 * time — a warm-pool player can arrive already in ERROR and get re-prepared (cleared) before
 * this watcher ever attaches, so the acquire site is the only place that error is observable.
 */
internal fun PlaybackException.describe(): String {
    val causeChain =
        generateSequence(cause) { it.cause }
            .joinToString(" <- ") { "${it::class.simpleName}: ${it.message}" }
            .ifEmpty { "none" }
    return "code=$errorCodeName($errorCode) msg=$message causes=[$causeChain]"
}

// How often the decode-stall watchdog samples the controller's position/buffer.
private const val STALL_POLL_INTERVAL_MS = 1_000L

// The decoder is only considered "fed" once it has comfortably more than bufferForPlaybackMs
// (750 ms in feedTunedLoadControl) of media ready ahead of the playhead. Below this we treat a
// frozen playhead as ordinary network starvation, not a decode failure, and leave it alone.
private const val STALL_MIN_BUFFER_AHEAD_MS = 2_000L

// How long the playhead may stay frozen while the decoder is fed before we declare the stream
// undecodable. A healthy player crosses bufferForPlaybackMs and starts rendering within a second
// or two, so 8 s of fed-but-frozen buffering is unambiguous.
private const val STALL_TIMEOUT_MS = 8_000L

// A live HLS stream (a FAST channel / kind:30311 broadcast) publishes only a short sliding window
// of segments — e.g. ~60 s — with no ENDLIST. If the playhead drifts before the start of that
// window (the player was prepared then held off the live edge, or a rebuffer outlasted the DVR
// depth), ExoPlayer raises ERROR_CODE_BEHIND_LIVE_WINDOW. That is not a terminal failure: the
// documented recovery is to seek back to the default (live) position and re-prepare.
//
// This is the ONLY error we auto-recover. An earlier version also re-prepared on any live I/O error
// (the 2xxx band), but that thrashed on a stream whose segments fail to parse
// (UnexpectedLoaderException / IllegalArgumentException): each re-prepare briefly reached READY then
// hit the same bad segment, and because the cap reset on READY it never gave up. I/O and decode
// errors are now left terminal — ExoPlayer's own LoadErrorHandlingPolicy already retries transient
// segment loads, and RenderPlaybackError shows the honest "open in browser" overlay for the rest.
internal const val MAX_LIVE_STREAM_RECOVERY_ATTEMPTS = 5

// The playhead must advance at least this far past the position an error was raised at before the
// recovery budget is refilled. Reaching READY alone is not enough: a stream that flaps READY for a
// fraction of a second at the same spot must exhaust the cap and surface, not loop forever.
private const val RECOVERY_RESET_PROGRESS_MS = 3_000L

/**
 * Whether a terminal [PlaybackException] should be recovered by seek-to-live + re-prepare rather
 * than surfaced. Only `ERROR_CODE_BEHIND_LIVE_WINDOW` qualifies — it is a rejoin signal, not a
 * failure. Everything else (I/O, decode, parse) is left terminal. Pure, so it is unit-testable.
 */
internal fun isRecoverableLiveError(errorCode: Int): Boolean = errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW

/**
 * Mirrors the MediaController's terminal-error state into [MediaControllerState.playbackError]
 * so [RenderPlaybackError] can show the codec-not-supported overlay with a browser fallback.
 *
 * ExoPlayer enters the ERROR state silently on decoder init / decode / format failures; the
 * ContentFrame then renders nothing and the user is left staring at a blank box. Surfacing the
 * exception gives [RenderVideoPlayer] something to render and lets the user open the URL
 * externally instead.
 *
 * Some codec failures never surface as a [PlaybackException] at all: a software HEVC decoder that
 * can't keep up (e.g. iPhone-recorded `hvc1` video on a device without a HEVC hardware decoder)
 * just parks the player in [Player.STATE_BUFFERING] forever — the buffer fills to the LoadControl
 * cap, yet the playhead never leaves 0 and no error is ever raised. [watchForDecodeStall] polls
 * for that signature and synthesizes a [PlaybackException] so the same overlay kicks in.
 */
@Composable
fun WatchPlaybackErrors(controllerState: MediaControllerState) {
    val controller = controllerState.controller
    val errorState = controllerState.playbackError

    DisposableEffect(controllerState) {
        // Prime from the controller's current state — a warm-pool player may already be in ERROR
        // when we attach, in which case onPlayerErrorChanged will not fire again until prepare().
        errorState.value = controller.playerError
        controller.playerError?.let {
            Log.w(ERROR_LOG_TAG) { "Primed with existing error on ${controller.currentMediaItem?.mediaId}: ${it.describe()}" }
            Log.d(PLAYBACK_DIAG_TAG) { "PRIMED with existing error on ${controller.currentMediaItem?.mediaId}: ${it.describe()}" }
        }

        // Re-preparations attempted for the current stream. Reset on a media-item change and once
        // playback makes real progress past the point an error was raised (see recoveryAnchorMs),
        // never merely on reaching READY — a sub-second READY flap must not refill the budget.
        var liveRecoveryAttempts = 0
        var recoveryAnchorMs = Long.MIN_VALUE

        val listener =
            object : Player.Listener {
                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    if (error != null) {
                        val mediaId = controller.currentMediaItem?.mediaId
                        val recoverable = isRecoverableLiveError(error.errorCode)
                        Log.d(PLAYBACK_DIAG_TAG) {
                            "ERROR code=${error.errorCodeName}(${error.errorCode}) recoverable=$recoverable " +
                                "attempts=$liveRecoveryAttempts pos=${controller.currentPosition} buffered=${controller.bufferedPosition} " +
                                "on $mediaId :: ${error.describe()}"
                        }
                        Log.w(ERROR_LOG_TAG) { "Error raised on $mediaId: ${error.describe()}" }

                        if (recoverable && liveRecoveryAttempts < MAX_LIVE_STREAM_RECOVERY_ATTEMPTS) {
                            liveRecoveryAttempts++
                            recoveryAnchorMs = controller.currentPosition
                            Log.d(PLAYBACK_DIAG_TAG) {
                                "RECOVER attempt $liveRecoveryAttempts/$MAX_LIVE_STREAM_RECOVERY_ATTEMPTS " +
                                    "after ${error.errorCodeName} on $mediaId — seekToDefaultPosition + prepare"
                            }
                            // Rejoin the live edge and re-prepare. Leave errorState null so the
                            // overlay never appears for a recoverable hiccup.
                            controller.seekToDefaultPosition()
                            controller.prepare()
                            return
                        }

                        Log.d(PLAYBACK_DIAG_TAG) {
                            val why = if (recoverable) "GAVE UP after $liveRecoveryAttempts attempts" else "TERMINAL (not recovering) ${error.errorCodeName}"
                            "$why on $mediaId — overlay will show"
                        }
                    } else if (errorState.value != null) {
                        Log.d(ERROR_LOG_TAG) { "Error cleared on ${controller.currentMediaItem?.mediaId}" }
                    }
                    errorState.value = error
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    Log.d(PLAYBACK_DIAG_TAG) { "ITEM reason=$reason -> ${mediaItem?.mediaId} (mime=${mediaItem?.localConfiguration?.mimeType})" }
                    liveRecoveryAttempts = 0
                    recoveryAnchorMs = Long.MIN_VALUE
                    // A new item on a pooled player starts fresh; drop any error from the old one.
                    if (errorState.value != null) {
                        Log.d(ERROR_LOG_TAG) { "Error dropped on media transition (reason=$reason) -> ${mediaItem?.mediaId}" }
                        errorState.value = null
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    Log.d(PLAYBACK_DIAG_TAG) {
                        "STATE=${playbackStateName(state)} playWhenReady=${controller.playWhenReady} " +
                            "pos=${controller.currentPosition} buffered=${controller.bufferedPosition} " +
                            "on ${controller.currentMediaItem?.mediaId}"
                    }
                    // A successful transition to READY (the renderer produced output) is the only
                    // real recovery — clear the overlay then. We deliberately do NOT clear on
                    // STATE_BUFFERING: the synthetic decode-stall error below is raised *while*
                    // buffering, and clearing on every buffering event would wipe it instantly.
                    if (state == Player.STATE_READY) {
                        // Refill the recovery budget only after genuine forward progress past the last
                        // error, so a stream that re-errors at the same spot every ~1 s still hits the
                        // cap and surfaces instead of thrashing forever.
                        if (controller.currentPosition >= recoveryAnchorMs + RECOVERY_RESET_PROGRESS_MS) {
                            liveRecoveryAttempts = 0
                            recoveryAnchorMs = Long.MIN_VALUE
                        }
                        if (errorState.value != null) {
                            Log.d(ERROR_LOG_TAG) { "Recovered (STATE_READY) on ${controller.currentMediaItem?.mediaId} — clearing overlay" }
                            errorState.value = null
                        }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    Log.d(PLAYBACK_DIAG_TAG) {
                        "DISCONTINUITY reason=$reason ${oldPosition.positionMs}ms -> ${newPosition.positionMs}ms " +
                            "on ${controller.currentMediaItem?.mediaId}"
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    Log.d(PLAYBACK_DIAG_TAG) {
                        "TRACKS video[${tracks.selectedVideoSummary()}] on ${controller.currentMediaItem?.mediaId}"
                    }
                }
            }

        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
        }
    }

    LaunchedEffect(controllerState) {
        watchForDecodeStall(controller, errorState)
    }
}

/**
 * Polls [controller] for the silent-decode-stall signature and, once seen continuously for
 * [STALL_TIMEOUT_MS], writes a synthetic [PlaybackException] so the browser-fallback overlay shows.
 *
 * Stall signature: the player wants to play and is stuck in [Player.STATE_BUFFERING] with at least
 * [STALL_MIN_BUFFER_AHEAD_MS] of media buffered ahead (so the decoder is fed, not network-starved),
 * yet the playhead has not advanced. A genuine mid-stream rebuffer fails the buffer-ahead guard —
 * its buffer is depleted, which is precisely why it rebuffers — so it is never flagged.
 */
@OptIn(UnstableApi::class)
private suspend fun watchForDecodeStall(
    controller: Player,
    errorState: MutableState<PlaybackException?>,
) {
    var unproductiveSinceMs = -1L
    var lastPosition = Long.MIN_VALUE

    // delay() is cancellable, so the loop exits when the LaunchedEffect is torn down.
    while (true) {
        delay(STALL_POLL_INTERVAL_MS)

        val position = controller.currentPosition
        val progressed = position != lastPosition
        lastPosition = position

        val decoderFedButFrozen =
            controller.playbackState == Player.STATE_BUFFERING &&
                controller.playWhenReady &&
                controller.bufferedPosition - position >= STALL_MIN_BUFFER_AHEAD_MS

        if (decoderFedButFrozen && !progressed) {
            val now = SystemClock.elapsedRealtime()
            if (unproductiveSinceMs < 0) {
                unproductiveSinceMs = now
            } else if (now - unproductiveSinceMs >= STALL_TIMEOUT_MS && errorState.value == null) {
                Log.w(ERROR_LOG_TAG) {
                    "Synthetic decode-stall after ${STALL_TIMEOUT_MS}ms fed-but-frozen " +
                        "(pos=$position buffered=${controller.bufferedPosition}) on ${controller.currentMediaItem?.mediaId}"
                }
                Log.d(PLAYBACK_DIAG_TAG) {
                    "SYNTHETIC-STALL fed-but-frozen ${STALL_TIMEOUT_MS}ms " +
                        "(pos=$position buffered=${controller.bufferedPosition}) on ${controller.currentMediaItem?.mediaId} — likely codec reconfig/unsupported"
                }
                errorState.value =
                    PlaybackException(
                        "Video decoding stalled with a full buffer — likely an unsupported codec",
                        null,
                        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    )
            }
        } else {
            unproductiveSinceMs = -1L

            // "If it works, just play": if the playhead is advancing again the stream is decoding
            // after all (a slow device that eventually produced a frame, a recovered hiccup), so
            // drop the stall overlay. Real decoder errors leave the player IDLE with a frozen
            // playhead, so they never progress here and are left for the STATE_READY listener.
            if (progressed && errorState.value != null) {
                Log.d(ERROR_LOG_TAG) { "Playhead progressed to $position — clearing stall overlay on ${controller.currentMediaItem?.mediaId}" }
                errorState.value = null
            }
        }
    }
}
