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

import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.nip71Video.CaptionTrack
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class MediaItemData(
    val videoUri: String,
    val authorName: String? = null,
    val title: String? = null,
    val artworkUri: String? = null,
    val callbackUri: String? = null,
    val mimeType: String? = null,
    val aspectRatio: Float? = null,
    val proxyPort: Int? = null,
    val keepPlaying: Boolean = true,
    val waveformData: WaveformData? = null,
    // True only for true live streams (e.g. kind 30311). On-demand HLS
    // (e.g. multi-rendition NIP-71 videos) must leave this false so that
    // ExoPlayer's SimpleCache can cache their immutable segments.
    val isLiveStream: Boolean = false,
    // Source-imeta visual fields — carried through so the player's "Add to Gallery" /
    // "Share" actions can publish a richer ProfileGalleryEntryEvent. Optional; null when
    // the surface that constructed this MediaItemData didn't have them.
    val blurhash: String? = null,
    val dim: DimensionTag? = null,
    val hash: String? = null,
    val thumbhash: String? = null,
    // NIP-71 `text-track` captions to side-load. A data class is the cache key, so this has to be
    // a value that compares by content — ImmutableList keeps that true and keeps the key @Stable.
    val captions: ImmutableList<CaptionTrack> = persistentListOf(),
    // Set by the in-post audio cards: picture-in-picture then draws the audio view (cover, waveform,
    // time) in a square window, unmuted, instead of a video surface with no picture.
    val isAudio: Boolean = false,
)

@Immutable
class LoadedMediaItem(
    val src: MediaItemData,
    val item: MediaItem,
)
