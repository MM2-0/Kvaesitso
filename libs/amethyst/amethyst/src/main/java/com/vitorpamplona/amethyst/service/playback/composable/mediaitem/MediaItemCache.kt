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

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.vitorpamplona.amethyst.commons.model.nip71Video.CaptionTrack
import com.vitorpamplona.amethyst.commons.util.cache.GenericBaseCache
import com.vitorpamplona.amethyst.commons.video.normalizeStreamMimeType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class MediaItemCache : GenericBaseCache<MediaItemData, LoadedMediaItem>(20) {
    companion object {
        const val EXTRA_CALLBACK_URI = "callbackUri"
        const val EXTRA_IS_LIVE_STREAM = "isLiveStream"

        // ExoPlayer's URI-based content-type inference only fires for http(s)
        // schemes + known extensions — BUD-10 blossom URIs have neither, so
        // without an explicit mimeType HLS playlists get routed to
        // ProgressiveMediaSource and fail.
        internal fun toExoPlayerMimeType(
            mimeType: String?,
            videoUri: String? = null,
        ): String? = normalizeStreamMimeType(mimeType, videoUri)

        // A side-loaded track needs its media type declared: a NIP-71 `text-track` says nothing
        // about the format, and a blossom-hosted one is `https://host/<sha256>` with no extension
        // for ExoPlayer to infer from either. A track flagged default is the one ExoPlayer
        // selects without being asked.
        internal fun CaptionTrack.toSubtitleConfiguration(isDefault: Boolean = false): MediaItem.SubtitleConfiguration =
            MediaItem.SubtitleConfiguration
                .Builder(url.toUri())
                .setMimeType(mimeType)
                .setLanguage(language)
                .setLabel(label())
                .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()

        private fun CaptionTrack.label(): String? =
            when {
                language != null && type != null -> "$language \u00B7 $type"
                else -> language ?: type
            }
    }

    override suspend fun compute(key: MediaItemData): LoadedMediaItem =
        withContext(Dispatchers.IO) {
            val normalizedMime = toExoPlayerMimeType(key.mimeType, key.videoUri)
            Log.d("MediaItemCache") {
                "compute: videoUri=${key.videoUri} imetaMime=${key.mimeType} -> exoMime=$normalizedMime isLive=${key.isLiveStream}"
            }
            LoadedMediaItem(
                key,
                MediaItem
                    .Builder()
                    .setMediaId(key.videoUri)
                    .setUri(key.videoUri)
                    .apply { normalizedMime?.let { setMimeType(it) } }
                    .setSubtitleConfigurations(
                        // Flagging the first track default is what decides WHICH track plays
                        // once text is enabled. WHETHER it is enabled is the account's
                        // captionsEnabled preference, which RenderTopButtons pushes onto the
                        // player as a track-type switch — the two are independent.
                        key.captions.mapIndexed { index, track ->
                            track.toSubtitleConfiguration(isDefault = index == 0)
                        },
                    ).setMediaMetadata(
                        MediaMetadata
                            .Builder()
                            .setArtist(key.authorName?.ifBlank { null })
                            .setTitle(key.title?.ifBlank { null } ?: key.videoUri)
                            .setExtras(
                                Bundle().apply {
                                    putString(EXTRA_CALLBACK_URI, key.callbackUri)
                                    putBoolean(EXTRA_IS_LIVE_STREAM, key.isLiveStream)
                                },
                            ).setArtworkUri(
                                try {
                                    key.artworkUri?.toUri()
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    null
                                },
                            ).build(),
                    ).build(),
            )
        }
}
