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
package com.vitorpamplona.amethyst.service.playback.composable.mediaitem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.nip71Video.CaptionTrack
import com.vitorpamplona.amethyst.commons.ui.state.produceCachedState
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

val mediaItemCache = MediaItemCache()

@Composable
fun GetMediaItem(
    videoUri: String,
    title: String? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    callbackUri: String? = null,
    mimeType: String? = null,
    aspectRatio: Float? = null,
    proxyPort: Int? = null,
    keepPlaying: Boolean = false,
    waveformData: WaveformData? = null,
    isLiveStream: Boolean = false,
    blurhash: String? = null,
    dim: DimensionTag? = null,
    hash: String? = null,
    thumbhash: String? = null,
    captions: ImmutableList<CaptionTrack> = persistentListOf(),
    inner: @Composable (LoadedMediaItem) -> Unit,
) {
    val data =
        // Captions join the key. They resolve asynchronously — a `text-track` that names a
        // kind-39307 coordinate has to reach LocalCache first — so keying on the URI alone would
        // lock in the caption-less item built on the first frame. The list only ever goes empty ->
        // resolved, so this rebuilds at most once.
        //
        // What that rebuild reaches depends on when it lands. A feed video is mounted long before
        // it is scrolled into view and played, so the item carrying the tracks is the one the
        // controller loads. For a video already playing, GetVideoController's warm-path check sees
        // the same mediaId and deliberately does not re-set the item, so the late tracks wait for
        // the next mount rather than restarting playback under the viewer.
        remember(videoUri, captions) {
            MediaItemData(
                videoUri = videoUri,
                authorName = authorName,
                title = title,
                artworkUri = artworkUri,
                callbackUri = callbackUri,
                mimeType = mimeType,
                aspectRatio = aspectRatio,
                proxyPort = proxyPort,
                keepPlaying = keepPlaying,
                waveformData = waveformData,
                isLiveStream = isLiveStream,
                blurhash = blurhash,
                dim = dim,
                hash = hash,
                thumbhash = thumbhash,
                captions = captions,
            )
        }

    GetMediaItem(data, inner)
}

@Composable
fun GetMediaItem(
    data: MediaItemData,
    inner: @Composable (LoadedMediaItem) -> Unit,
) {
    val mediaItem by produceCachedState(cache = mediaItemCache, key = data)

    // produceCachedState re-remembers on a new key and starts at null while the replacement is
    // built off-thread. Rendering that null would drop the whole player subtree for a frame and
    // rebuild it — a visible blink — and for the one case that changes the key mid-playback
    // (captions resolving after the first frame) the warm-pool check then declines to re-set an
    // item carrying the same mediaId, so the blink would buy nothing. Keep drawing the item we
    // already have until its replacement exists.
    val previous = remember { mutableStateOf<LoadedMediaItem?>(null) }
    val current = mediaItem
    if (current != null && previous.value !== current) {
        SideEffect { previous.value = current }
    }

    (current ?: previous.value)?.let {
        inner(it)
    }
}
