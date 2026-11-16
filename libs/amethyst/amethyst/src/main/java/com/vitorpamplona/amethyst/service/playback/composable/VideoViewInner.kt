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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.nip71Video.CaptionTrack
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.mainVideo.VideoPlayerActiveMutex
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

val DEFAULT_MUTED_SETTING = mutableStateOf(true)

@Composable
fun VideoViewInner(
    videoUri: String,
    mimeType: String?,
    aspectRatio: Float? = null,
    title: String? = null,
    thumb: VideoThumb? = null,
    showControls: Boolean = true,
    contentScale: ContentScale,
    borderModifier: Modifier,
    waveform: WaveformData? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    nostrUriCallback: String? = null,
    automaticallyStartPlayback: Boolean,
    isLiveStream: Boolean = false,
    // Remembered: an unremembered default made a new state on every recomposition, so a live
    // stream lost its tap-to-show controls once its kind 30311 event was republished. Keyed on
    // the URI so a new video in the same slot starts with the controls hidden.
    controllerVisible: MutableState<Boolean> = remember(videoUri) { mutableStateOf(false) },
    onZoom: (() -> Unit)? = null,
    hasBlurhash: Boolean = false,
    isFullscreen: Boolean = false,
    blurhash: String? = null,
    dim: DimensionTag? = null,
    hash: String? = null,
    thumbhash: String? = null,
    captions: ImmutableList<CaptionTrack> = persistentListOf(),
    accountViewModel: AccountViewModel,
) {
    // keeps a copy of the value to avoid recompositions here when the DEFAULT value changes
    val muted = remember(videoUri) { DEFAULT_MUTED_SETTING.value }

    // The proxy port is decided once per video URI; recomputing on every recomposition does
    // pointless work (the result is anyway dropped because GetMediaItem.remember is keyed on the
    // URI alone, so the cached MediaItemData is locked in on the first frame).
    val proxyPort = remember(videoUri) { accountViewModel.httpClientBuilder.proxyPortForVideo(videoUri) }

    GetMediaItem(
        videoUri = videoUri,
        title = title,
        artworkUri = artworkUri,
        authorName = authorName,
        callbackUri = nostrUriCallback,
        mimeType = mimeType,
        aspectRatio = aspectRatio,
        proxyPort = proxyPort,
        keepPlaying = true,
        waveformData = waveform,
        isLiveStream = isLiveStream,
        blurhash = blurhash,
        dim = dim,
        hash = hash,
        thumbhash = thumbhash,
        captions = captions,
    ) { mediaItem ->
        GetVideoController(
            mediaItem = mediaItem,
            muted = muted,
        ) { controller ->
            VideoPlayerActiveMutex(controller) { videoModifier, isClosestToTheCenterOfTheScreen ->
                ControlWhenPlayerIsActive(controller, automaticallyStartPlayback, isClosestToTheCenterOfTheScreen)
                RenderVideoPlayer(
                    mediaItem = mediaItem,
                    controllerState = controller,
                    thumbData = thumb,
                    showControls = showControls,
                    contentScale = contentScale,
                    borderModifier = borderModifier,
                    videoModifier = videoModifier,
                    controllerVisible = controllerVisible,
                    onDialog = onZoom,
                    hasBlurhash = hasBlurhash,
                    isFullscreen = isFullscreen,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}
