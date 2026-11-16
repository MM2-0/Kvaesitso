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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.model.nip71Video.CaptionTrack
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.commons.ui.components.IconWithSizeLabel
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaCapture
import com.vitorpamplona.amethyst.commons.ui.components.MediaDownloadCard
import com.vitorpamplona.amethyst.commons.ui.components.MediaDownloadRow
import com.vitorpamplona.amethyst.commons.ui.components.VideoStillFrame
import com.vitorpamplona.amethyst.commons.ui.components.mediaKindOf
import com.vitorpamplona.amethyst.commons.ui.components.rememberMediaSize
import com.vitorpamplona.amethyst.commons.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.commons.ui.theme.Size75dp
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.ui.theme.videoGalleryModifier
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ui.components.unknownMediaAspectRatio
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun VideoView(
    videoUri: String,
    mimeType: String?,
    title: String? = null,
    thumb: VideoThumb? = null,
    roundedCorner: Boolean,
    gallery: Boolean = false,
    contentScale: ContentScale,
    waveform: WaveformData? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    dimensions: DimensionTag? = null,
    blurhash: String? = null,
    nostrUriCallback: String? = null,
    onDialog: (() -> Unit)? = null,
    accountViewModel: AccountViewModel,
    alwaysShowVideo: Boolean = false,
    thumbhash: String? = null,
    isLiveStream: Boolean = false,
    hash: String? = null,
    captions: ImmutableList<CaptionTrack> = persistentListOf(),
    // The size the event declares: a held-back placeholder shows it (else it asks the host).
    sizeBytes: Long? = null,
) {
    val borderModifier =
        if (roundedCorner) {
            MaterialTheme.colorScheme.imageModifier
        } else if (gallery) {
            MaterialTheme.colorScheme.videoGalleryModifier
        } else {
            Modifier
        }

    VideoView(videoUri, mimeType, title, thumb, borderModifier, contentScale, waveform, artworkUri, authorName, dimensions, blurhash, nostrUriCallback, onDialog, alwaysShowVideo, accountViewModel = accountViewModel, thumbhash = thumbhash, isLiveStream = isLiveStream, hash = hash, captions = captions, sizeBytes = sizeBytes)
}

@Composable
fun VideoView(
    videoUri: String,
    mimeType: String?,
    title: String? = null,
    thumb: VideoThumb? = null,
    borderModifier: Modifier,
    contentScale: ContentScale,
    waveform: WaveformData? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    dimensions: DimensionTag? = null,
    blurhash: String? = null,
    nostrUriCallback: String? = null,
    onDialog: (() -> Unit)? = null,
    alwaysShowVideo: Boolean = false,
    showControls: Boolean = true,
    isLiveStream: Boolean = false,
    accountViewModel: AccountViewModel,
    thumbhash: String? = null,
    hash: String? = null,
    captions: ImmutableList<CaptionTrack> = persistentListOf(),
    // The size the event declares: a held-back placeholder shows it (else it asks the host).
    sizeBytes: Long? = null,
) {
    val initialAutoStart = if (alwaysShowVideo) true else accountViewModel.settings.startVideoPlayback()
    // Reset the manual-show toggle when the video URI changes so a recycled feed slot
    // doesn't inherit "tapped to show" state from a prior video.
    val automaticallyStartPlayback = remember(videoUri) { mutableStateOf(initialAutoStart) }

    // Once the video is being shown, only honor the user's autoplay preference when it was auto-loaded.
    // If the user manually tapped the download button, they want it to play.
    // Never inside a share-as-image capture: audio keeps its player there, unseen and unstoppable.
    val autoplay =
        LocalMediaCapture.current == null &&
            (alwaysShowVideo || (initialAutoStart && accountViewModel.settings.autoPlayVideos()) || (!initialAutoStart && automaticallyStartPlayback.value))

    // Resolve the aspect ratio once per composition. Prime the URL-keyed cache from the imeta
    // dim tag so the next time this video appears (PiP, dialog, list re-enter) the cache hits
    // without waiting for ExoPlayer's onVideoSizeChanged. Keys are primitive width/height so
    // a freshly parsed DimensionTag instance for the same event doesn't re-run this lambda —
    // DimensionTag uses reference equality, not structural.
    val dimW = dimensions?.width
    val dimH = dimensions?.height
    // Deliberately snapshotted in a remember rather than observing MediaAspectRatioCache: when the
    // ratio flips null -> known mid-playback this branch both adds an aspectRatio and emits an
    // extra Spacer, and restructuring the children around a live AndroidView leaves the player's
    // TextureView on a stale surface (the video redraws at native size in the corner). The
    // enclosing box in ZoomableContentView is what sizes the player, and that one does observe.
    val snapshotRatio =
        remember(videoUri, dimW, dimH) {
            if (dimW != null && dimH != null && dimW > 0 && dimH > 0) {
                MediaAspectRatioCache.add(videoUri, dimW, dimH)
                MediaAspectRatioCache.videoRatio(videoUri, dimW.toFloat() / dimH.toFloat())
            } else {
                MediaAspectRatioCache.get(videoUri)
            }
        }
    // Once there is a ratio, what the player measures replaces it as it lands: a ratio swapped for
    // a ratio keeps the children as they are, and it corrects a `dim` the uploader got wrong (the
    // stored size of an anamorphic video), which would otherwise crop the picture.
    val ratio = snapshotRatio?.let { MediaAspectRatioCache.measured(videoUri) ?: it }

    // A player surface can't be recorded into a share-as-image capture: draw a still instead.
    if (LocalMediaCapture.current != null && waveform == null && !RichTextParser.isAudioContent(mimeType, videoUri)) {
        // Coil's VideoFrameDecoder reads the first frame of a file, which means downloading all of
        // it: only where the user lets videos load. An HLS playlist has no frame to decode.
        val frameFromVideo = !isLiveStream && !isHlsMedia(videoUri, mimeType) && accountViewModel.settings.startVideoPlayback()
        VideoStillFrame(
            model = artworkUri ?: videoUri.takeIf { frameFromVideo },
            ratio = ratio,
            blurhash = blurhash,
            thumbhash = thumbhash,
            borderModifier = borderModifier,
            contentDescription = title,
        )
        return
    }

    // Only a held-back file asks the host for its size.
    val kind = mediaKindOf(mimeType, videoUri)
    val size = rememberMediaSize(videoUri, kind, accountViewModel, mimeType, sizeBytes, held = !automaticallyStartPlayback.value)

    if (blurhash == null && thumbhash == null) {
        val modifier =
            if (ratio != null && automaticallyStartPlayback.value) {
                Modifier.aspectRatio(ratio)
            } else {
                Modifier
            }

        Box(modifier) {
            if (!automaticallyStartPlayback.value) {
                // The card fills the box the player will take, instead of a line floating in an empty
                // area of that size. A video that declares no shape gets the same 16:9 the enclosing
                // box falls back to; only audio, which has no frame, keeps the compact row.
                val cardRatio = ratio ?: unknownMediaAspectRatio(mimeType, videoUri)
                if (cardRatio != null) {
                    MediaDownloadCard(videoUri, kind, size, cardRatio, borderModifier) { automaticallyStartPlayback.value = true }
                } else {
                    MediaDownloadRow(videoUri, kind, size, Modifier) { automaticallyStartPlayback.value = true }
                }
            } else {
                // Sized backdrop while the MediaController IPC is in flight and ContentFrame
                // hasn't mounted yet. Only emitted when the Box already has an aspectRatio
                // modifier — fillMaxSize requires bounded constraints, which the ratio
                // provides. Mirrors the DisplayBlurHash backdrop in the blurhash branch.
                if (ratio != null) {
                    Spacer(Modifier.fillMaxSize())
                }
                VideoViewInner(
                    videoUri = videoUri,
                    mimeType = mimeType,
                    aspectRatio = ratio,
                    title = title,
                    thumb = thumb,
                    borderModifier = borderModifier,
                    contentScale = contentScale,
                    waveform = waveform,
                    artworkUri = artworkUri,
                    authorName = authorName,
                    nostrUriCallback = nostrUriCallback,
                    automaticallyStartPlayback = autoplay,
                    isLiveStream = isLiveStream,
                    onZoom = onDialog,
                    hasBlurhash = false,
                    blurhash = blurhash,
                    dim = dimensions,
                    hash = hash,
                    thumbhash = thumbhash,
                    captions = captions,
                    accountViewModel = accountViewModel,
                    showControls = showControls,
                )
            }
        }
    } else {
        val modifier =
            if (ratio != null) {
                Modifier.aspectRatio(ratio)
            } else {
                Modifier
            }

        Box(modifier, contentAlignment = Alignment.Center) {
            // Always displays a placeholder (thumbhash preferred, blurhash fallback) to avoid size flickering
            DisplayBlurHash(
                blurhash,
                null,
                // The placeholder bitmap is decoded at the blurhash's DCT component-grid aspect
                // (e.g. a 5x5 grid -> a square bitmap), NOT the real media shape. When `ratio` is
                // known the Box is already sized to the true aspect, so crop the placeholder to
                // fill it. Without this, FillWidth letterboxes the square placeholder inside the
                // taller portrait box — the "square blurhash on a twice-as-tall space" bug.
                if (ratio != null) ContentScale.Crop else contentScale,
                if (ratio != null) borderModifier.aspectRatio(ratio) else borderModifier,
                thumbhash = thumbhash,
            )

            if (!automaticallyStartPlayback.value) {
                IconWithSizeLabel(kind, size) {
                    IconButton(
                        modifier = Modifier.size(Size75dp),
                        onClick = { automaticallyStartPlayback.value = true },
                    ) {
                        DownloadForOfflineIcon(Size75dp, Color.White)
                    }
                }
            } else {
                VideoViewInner(
                    videoUri = videoUri,
                    mimeType = mimeType,
                    aspectRatio = ratio,
                    title = title,
                    thumb = thumb,
                    borderModifier = borderModifier,
                    contentScale = contentScale,
                    waveform = waveform,
                    artworkUri = artworkUri,
                    authorName = authorName,
                    nostrUriCallback = nostrUriCallback,
                    automaticallyStartPlayback = autoplay,
                    isLiveStream = isLiveStream,
                    onZoom = onDialog,
                    hasBlurhash = true,
                    blurhash = blurhash,
                    dim = dimensions,
                    hash = hash,
                    thumbhash = thumbhash,
                    captions = captions,
                    accountViewModel = accountViewModel,
                    showControls = showControls,
                )
            }
        }
    }
}
