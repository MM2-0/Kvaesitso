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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.audio.mediaFormatLabel
import com.vitorpamplona.amethyst.commons.audio.player.AudioCardInfo
import com.vitorpamplona.amethyst.commons.audio.player.AudioPipContent
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.service.playback.composable.rememberAudioPlaybackUi

/**
 * The picture-in-picture window for audio sent there from an in-post audio card: the cover (artwork,
 * else the one generated from the file hash), the waveform as progress, and the time. There is no
 * picture to show, so unlike [RenderPipVideo] it draws no video surface and doesn't hold the screen on —
 * audio keeps playing with the screen off.
 */
@Composable
fun RenderPipAudio(
    controller: MediaControllerState,
    mediaItemData: MediaItemData,
) {
    val info =
        remember(mediaItemData) {
            AudioCardInfo(
                title = null,
                artist = mediaItemData.authorName,
                format = mediaFormatLabel(mediaItemData.mimeType, mediaItemData.videoUri),
                sizeBytes = null,
                seed = mediaItemData.hash ?: mediaItemData.videoUri,
                waveform = mediaItemData.waveformData?.wave,
                artworkUrl = mediaItemData.artworkUri,
            )
        }
    val playback by rememberAudioPlaybackUi(controller.controller)
    AudioPipContent(info, playback)
}
