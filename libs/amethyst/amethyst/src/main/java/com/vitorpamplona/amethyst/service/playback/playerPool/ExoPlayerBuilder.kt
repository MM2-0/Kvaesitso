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
package com.vitorpamplona.amethyst.service.playback.playerPool

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.service.playback.VideoViewedPositionCache
import com.vitorpamplona.amethyst.service.nowPlaying.InAppPlaybackListener
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.amethyst.service.playback.playerPool.aspectRatio.AspectRatioCacher
import com.vitorpamplona.amethyst.service.playback.playerPool.positions.CurrentPlayPositionCacher
import com.vitorpamplona.amethyst.service.playback.playerPool.repeat.AutoReplayLimiter
import com.vitorpamplona.amethyst.service.playback.playerPool.wake.KeepVideosPlaying

@OptIn(UnstableApi::class)
class ExoPlayerBuilder(
    val videoCache: VideoCache,
    val dataSourceFactory: DataSource.Factory,
) {
    fun build(context: Context): ExoPlayer {
        val sink = PcmTapRegistry.newSink()

        val renderersFactory =
            object : DefaultRenderersFactory(context) {
                @Suppress("DEPRECATION") // media3 DefaultAudioSink.Builder.setEnableAudioTrackPlaybackParams is deprecated.
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink =
                    DefaultAudioSink
                        .Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(arrayOf(TeeAudioProcessor(sink)))
                        .build()
            }

        return ExoPlayer
            .Builder(context, renderersFactory)
            .apply {
                setMediaSourceFactory(CustomMediaSourceFactory(videoCache, dataSourceFactory))
                setLoadControl(feedTunedLoadControl())
            }.build()
            .apply {
                PcmTapRegistry.registerPlayer(this, sink)
                addListener(
                    object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int,
                        ) {
                            PcmTapRegistry.bind(mediaItem?.mediaId, sink)
                        }
                    },
                )
                PcmTapRegistry.bind(currentMediaItem?.mediaId, sink)
                addListener(AspectRatioCacher(MediaAspectRatioCache))
                addListener(HlsLivenessRecorder(this))
                addListener(AutoReplayLimiter(pause = ::pause))
                addListener(KeepVideosPlaying(this))
                addListener(CurrentPlayPositionCacher(this, VideoViewedPositionCache))
                addListener(MediaPlayTimeTracker())
                addListener(InAppPlaybackListener())
            }
    }

    companion object {
        // Default DefaultLoadControl buffers 50s ahead before slowing down. Every visible video
        // in the feed is prepared eagerly so it's ready when the user scrolls to it; with the
        // default settings 5 simultaneous preloads would fight for ~250s of buffer between them
        // and chew through ~30+ MB per HD player. Feed playback is optimized for "the active
        // video plays smoothly while a few neighbours stay warm," so we cap the buffer at ~15s
        // and let playback kick in as soon as ~750 ms is buffered. Fullscreen still gets a
        // healthy buffer because seeks-within-15s are virtually instant from disk cache.
        private fun feedTunedLoadControl() =
            DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(
                    // minBufferMs =
                    10_000,
                    // maxBufferMs =
                    15_000,
                    // bufferForPlaybackMs =
                    750,
                    // bufferForPlaybackAfterRebufferMs =
                    2_000,
                ).setPrioritizeTimeOverSizeThresholds(true)
                .build()
    }
}
