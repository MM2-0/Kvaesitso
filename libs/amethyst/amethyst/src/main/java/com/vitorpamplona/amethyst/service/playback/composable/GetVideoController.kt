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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.LoadedMediaItem
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.service.playback.service.PlaybackServiceClient
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal const val BACKGROUND_RELEASE_TIMEOUT_MS = 30_000L

@Composable
fun GetVideoController(
    mediaItem: LoadedMediaItem,
    muted: Boolean = false,
    play: Boolean = false,
    // Opt-out for callers whose lifecycle owner is already a background-playback
    // surface (PiP): there, the 30s timer would fire as soon as the activity
    // enters PiP mode and race the controller build / `RegisterBackgroundMedia`
    // registration, killing the just-attached controller and blanking the
    // window. The opt-out skips the timer entirely for those callers.
    releaseOnBackgroundTimeout: Boolean = true,
    inner: @Composable (mediaControllerState: MediaControllerState) -> Unit,
) {
    val context = LocalContext.current

    // After the app has been in the background for BACKGROUND_RELEASE_TIMEOUT_MS,
    // drop the MediaController so the underlying ExoPlayer + codec/buffer can be
    // returned to the pool. On resume the flow is rebuilt, the new session reuses
    // the same paused player from the warm pool (keyed by URI), and the onEach
    // warm-pool fast path keeps position and buffered data intact.
    val keepAlive = remember { mutableStateOf(true) }

    // Captions can resolve *after* playback starts: a `text-track` tag naming a kind-39307
    // coordinate is only turned into a SubtitleConfiguration once that event arrives, which
    // rebuilds `mediaItem` with the same videoUri and a different subtitle list. The mediaId is
    // the video URI either way, so the warm-pool fast path below would read "already loaded" and
    // the new tracks would never reach the player. A MediaController doesn't carry the item's
    // `localConfiguration` across the service IPC, so the subtitles can't be read back off the
    // controller to compare — we remember what we last pushed instead. Deliberately keyless so it
    // survives the keyed remember below being rebuilt by the very change it has to detect.
    val lastAppliedCaptions = remember { mutableStateOf<Int?>(null) }

    val controllerState by remember(mediaItem, keepAlive.value) {
        if (keepAlive.value) {
            PlaybackServiceClient
                .controllerAsFlow(
                    videoUri = mediaItem.src.videoUri,
                    proxyPort = mediaItem.src.proxyPort,
                    keepPlaying = mediaItem.src.keepPlaying,
                    context = context,
                ).onEach { state ->
                    Log.d("PlaybackService") { "Controller instance: ${state.controller}" }

                    // A warm-pool ExoPlayer can be handed back still carrying a prior
                    // PlaybackException (e.g. a decoder-init failure from an earlier acquire). The
                    // re-prepare below clears it before WatchPlaybackErrors ever attaches, so this
                    // is the only place the stale error — and its decoder/codec cause chain — is
                    // observable. Logged so a "Can't play this video" blink that self-heals can be
                    // attributed to warm-pool reuse rather than a genuinely undecodable stream.
                    state.controller.playerError?.let { err ->
                        Log.w(ERROR_LOG_TAG) { "Controller arrived carrying error for ${mediaItem.item.mediaId}: ${err.describe()}" }
                    }

                    // The default ExoPlayer volume is 1f and the MediaSessionPool reset lambda
                    // sets it to 0f when the player is acquired, so the controller arrives at 0f.
                    // Read first and only push an IPC if the value actually needs to change —
                    // with several feed videos preloading at once each volume= write was a
                    // round-trip to the service for nothing.
                    val targetVolume =
                        when {
                            BackgroundMedia.isPlaying() -> 0f
                            muted -> 0f
                            else -> 1f
                        }
                    if (state.controller.volume != targetVolume) {
                        state.controller.volume = targetVolume
                        Log.d("PlaybackService") { "OnEach volume=$targetVolume" }
                    }

                    if (play) {
                        state.controller.playWhenReady = true
                    }

                    // Warm-pool fast path: when the underlying ExoPlayer was retained paused-with-
                    // buffer for this exact MediaItem, the MediaController's local mirror already
                    // shows the matching mediaId. Calling setMediaItem in that case would reset the
                    // player and discard the buffer — exactly what the warm pool exists to avoid.
                    // We still re-prepare if the player ended up IDLE somehow (e.g. it was demoted
                    // to cold and resurfaced, or hit an error before we attached).
                    val targetMediaId = mediaItem.item.mediaId
                    val captionsFingerprint = mediaItem.src.captions.hashCode()
                    val needsLoad = state.controller.currentMediaItem?.mediaId != targetMediaId
                    if (needsLoad) {
                        // Cold load: a fresh decoder/codec instance gets allocated here. If a
                        // second controller for the same URI is still alive (see liveControllers
                        // in PlaybackServiceClient), this prepare() is where MediaCodec.start()
                        // can collide and fail.
                        Log.d("PlaybackService") { "Cold load (setMediaItem+prepare) for $targetMediaId" }
                        state.controller.setMediaItem(mediaItem.item)
                        state.controller.prepare()
                        lastAppliedCaptions.value = captionsFingerprint
                    } else if (lastAppliedCaptions.value != null && lastAppliedCaptions.value != captionsFingerprint) {
                        // Same video, new caption set, and this composable is the one that loaded
                        // it: re-set the item so the side-loaded subtitle sources get merged in.
                        // Restarting from the current position keeps the rebuffer down to whatever
                        // the cache can't serve. The `!= null` guard is what keeps a warm player
                        // that arrived already carrying someone else's item from being re-prepared
                        // on a fingerprint we never pushed — that's the rebuffer the pool exists
                        // to avoid, and its captions are whatever that mount applied.
                        val resumeAt = state.controller.currentPosition
                        Log.d("PlaybackService") { "Captions changed for $targetMediaId - re-applying at $resumeAt" }
                        state.controller.setMediaItem(mediaItem.item, resumeAt)
                        state.controller.prepare()
                        lastAppliedCaptions.value = captionsFingerprint
                    } else if (state.controller.playbackState == Player.STATE_IDLE) {
                        Log.d("PlaybackService") { "Warm controller in STATE_IDLE — re-preparing" }
                        state.controller.prepare()
                    }
                }
        } else {
            flowOf<MediaControllerState?>(null)
        }
    }.collectAsState(null)

    if (releaseOnBackgroundTimeout) {
        ReleaseControllerWhenBackgroundedFor(
            timeoutMs = BACKGROUND_RELEASE_TIMEOUT_MS,
            controllerState = controllerState,
            keepAlive = keepAlive,
        )
    }

    controllerState?.let {
        inner(it)
    }
}

/**
 * Flips [keepAlive] to `false` after the host activity has been at ON_PAUSE for
 * [timeoutMs], so the gated flow upstream releases the MediaController. ON_RESUME
 * cancels any pending timer and flips it back to `true` so the controller is
 * reacquired.
 *
 * The BackgroundMedia (PiP) controller is exempt — it's opted into background
 * playback and must keep its MediaController alive past the timeout.
 */
@Composable
private fun ReleaseControllerWhenBackgroundedFor(
    timeoutMs: Long,
    controllerState: MediaControllerState?,
    keepAlive: MutableState<Boolean>,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentControllerState by rememberUpdatedState(controllerState)

    DisposableEffect(lifecycleOwner, keepAlive) {
        val scope = CoroutineScope(Dispatchers.Main)
        var timeoutJob: Job? = null

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        timeoutJob?.cancel()
                        timeoutJob =
                            scope.launch {
                                delay(timeoutMs)
                                val cs = currentControllerState
                                if (cs == null || !BackgroundMedia.isMutex(cs)) {
                                    keepAlive.value = false
                                }
                            }
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        timeoutJob?.cancel()
                        timeoutJob = null
                        keepAlive.value = true
                    }

                    else -> Unit
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            timeoutJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            scope.cancel()
        }
    }
}
