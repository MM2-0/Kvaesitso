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
package com.vitorpamplona.amethyst.service.playback.service

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object PlaybackServiceClient {
    // Runs the MediaController.buildAsync() completion callbacks. The work per callback is
    // trivial in the steady state (Future.get() on an already-completed future + a non-blocking
    // trySend into this video's own callbackFlow channel), so the IPC bind itself dominates and
    // happens on Media3's own threads regardless. We size the pool small enough to stay bounded
    // under churn but parallel enough that one stuck listener (e.g. the defensive 5s get()
    // timeout actually firing) can't stall the rest of the videos onscreen behind it. The
    // original newCachedThreadPool was unbounded and could spin up a thread per concurrent
    // video, each lingering for the 60s keep-alive afterwards.
    val executorService: ExecutorService = Executors.newFixedThreadPool(4)

    // Number of MediaControllers currently held alive (prepared and not yet released). Two
    // controllers alive for the same videoUri at once is the signature of the decoder-init
    // collision that surfaces as a transient "Can't play this video": the second one's
    // MediaCodec.start() fails because the first still holds a codec instance. Logged on every
    // prepare/release so the overlap is visible in a field logcat.
    private val liveControllers = AtomicInteger(0)

    fun shutdown() {
        executorService.shutdown()
    }

    @OptIn(ExperimentalUuidApi::class)
    fun controllerAsFlow(
        videoUri: String,
        proxyPort: Int? = 0,
        keepPlaying: Boolean = true,
        context: Context,
    ) = callbackFlow {
        val appContext = context.applicationContext
        val id = Uuid.random().toString()

        val bundle =
            Bundle().apply {
                // link the id with the client's id to make sure it can return the
                // same session on background media.
                putString(PlaybackService.HINT_ID, id)
                putBoolean(PlaybackService.HINT_KEEP_PLAYING, keepPlaying)
                proxyPort?.let {
                    putInt(PlaybackService.HINT_PROXY_PORT, it)
                }
                // Carry the URI so the service can ask the player pool for an existing warm
                // (paused-with-buffer) ExoPlayer that already holds this MediaItem. Falls back
                // gracefully — if no warm match exists, the pool returns a cold player.
                putString(PlaybackService.HINT_VIDEO_URI, videoUri)
            }

        val session = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))

        val controllerFuture =
            MediaController
                .Builder(appContext, session)
                .setConnectionHints(bundle)
                .buildAsync()

        Log.d("PlaybackService") { "Preparing Controller $id (live=${liveControllers.incrementAndGet()}) $videoUri" }

        controllerFuture.addListener(
            {
                try {
                    val controller = controllerFuture.get(5, TimeUnit.SECONDS)

                    Log.d("PlaybackService") { "Controller Ready $id $videoUri" }

                    // checks if the player is still active before engaging further
                    trySend(
                        MediaControllerState(
                            id = id,
                            controller = controller,
                        ),
                    )
                } catch (e: Exception) {
                    Log.e("Playback Client", "Failed to load Playback Client for $id $videoUri", e)
                    close(e)
                }
            },
            executorService,
        )

        awaitClose {
            Log.d("PlaybackService") { "Releasing Controller $id (live=${liveControllers.decrementAndGet()}) $videoUri" }
            try {
                MediaController.releaseFuture(controllerFuture)
            } catch (e: Exception) {
                Log.e("Playback Client") { "Failed to release Playback Client for $id $videoUri ${e.message}" }
            }
        }
    }
}
