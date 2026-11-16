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
package com.vitorpamplona.amethyst.service.playback.pip

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData

class PipVideoActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val videoData by rememberVideoDataFromIntents()
            videoData?.let { mediaItemData ->
                // keeps a copy of the value to avoid recompositions here when the DEFAULT value changes.
                // Audio is never started muted: the feed's mute default is about silent autoplaying
                // video, and an audio track somebody just sent to PiP is meant to be heard.
                val muted = remember(mediaItemData) { !mediaItemData.isAudio && DEFAULT_MUTED_SETTING.value }

                GetMediaItem(mediaItemData) { mediaItem ->
                    GetVideoController(
                        mediaItem = mediaItem,
                        muted = muted,
                        play = true,
                        // PiP IS the opt-in for background playback — never release on background.
                        releaseOnBackgroundTimeout = false,
                    ) { controllerState ->
                        RegisterBackgroundMedia(controllerState)
                        RegisterControllerReceiver(controllerState)
                        WatchControllerForActions(mediaItemData, controllerState)
                        if (mediaItemData.isAudio) {
                            RenderPipAudio(controllerState, mediaItemData)
                        } else {
                            RenderPipVideo(controllerState, mediaItemData.waveformData)
                        }
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            // User dismissed PiP (swiped away or expanded).
            finishAndRemoveTask()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            // Only finish if we're not in PiP mode.
            // When the screen locks while in PiP, we stay alive
            // so the PlaybackService can continue audio playback.
            finishAndRemoveTask()
        }
    }

    companion object {
        fun callIn(
            videoData: MediaItemData,
            videoBounds: Rect?,
            context: Context,
        ) {
            val options =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityOptions.makeLaunchIntoPip(
                        makePipParams(MediaAspectRatioCache.videoRatio(videoData.videoUri, videoData.aspectRatio), videoBounds),
                    )
                } else {
                    ActivityOptions.makeBasic()
                }

            context.startActivity(
                Intent(context.applicationContext, PipVideoActivity::class.java).apply {
                    putExtras(IntentExtras.createBundle(videoData, videoBounds))
                },
                options.toBundle(),
            )
        }
    }
}
