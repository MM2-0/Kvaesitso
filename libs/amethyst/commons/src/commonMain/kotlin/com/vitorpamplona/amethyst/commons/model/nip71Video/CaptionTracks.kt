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
package com.vitorpamplona.amethyst.commons.model.nip71Video

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.nip71Video.tags.TextTrackTag
import com.vitorpamplona.quartz.nip71Video.textTrack.TextTrackEvent

/**
 * One timed-text track a player can side-load, resolved from a video's `text-track` tag.
 *
 * [url] is what the player fetches. NIP-71 never says what media type a track is served as, and a
 * blossom-hosted one has no file extension either, so [mimeType] is always declared — WebVTT
 * unless the referenced [TextTrackEvent] says otherwise.
 */
@Immutable
data class CaptionTrack(
    val url: String,
    val mimeType: String = TextTrackEvent.DEFAULT_MIME_TYPE,
    val language: String? = null,
    // NIP-71 calls this the "type of supplementary information": captions, subtitles, chapters or
    // metadata. Kept as published so a track picker can label and group by it.
    val type: String? = null,
)

/**
 * Splits a video's `text-track` tags into the ones a player can load on its own and the ones that
 * first need a [TextTrackEvent] fetched.
 *
 * A `text-track` ref is either a URL (load it) or an addressable coordinate (resolve it). The same
 * track is routinely published both ways — divine.video emits the Blossom URL and the
 * `39307:<pubkey>:subtitles:<d>` coordinate side by side — so the two lists overlap by design and
 * [mergeCaptionTracks] is what de-duplicates them once the events are in hand.
 */
fun VideoEvent.captionTracks(): ResolvedCaptionTracks {
    val direct = mutableListOf<CaptionTrack>()
    val pending = mutableListOf<PendingCaptionTrack>()

    textTrack().forEach { tag ->
        val address = Address.parse(tag.ref)
        if (address != null) {
            pending.add(PendingCaptionTrack(address, tag))
        } else if (tag.ref.startsWith("http://", true) || tag.ref.startsWith("https://", true)) {
            direct.add(CaptionTrack(url = tag.ref, language = tag.language, type = tag.type))
        }
        // Anything else — a bare event id, per NIP-71's own example — is not loadable as a file
        // and has no address to resolve, so it is dropped rather than handed to the player as a
        // URL it would fail to fetch.
    }

    return ResolvedCaptionTracks(direct, pending)
}

@Immutable
data class ResolvedCaptionTracks(
    val direct: List<CaptionTrack>,
    val pending: List<PendingCaptionTrack>,
)

@Immutable
data class PendingCaptionTrack(
    val address: Address,
    val tag: TextTrackTag,
)

/** The track a resolved [TextTrackEvent] contributes, or null when it hosts no fetchable file. */
fun TextTrackEvent.toCaptionTrack(tag: TextTrackTag? = null): CaptionTrack? {
    val url = url() ?: return null
    return CaptionTrack(
        url = url,
        mimeType = mimeType() ?: TextTrackEvent.DEFAULT_MIME_TYPE,
        // The event is the more specific source: it was written by whoever made the track, while
        // the tag on the video is the video author's description of it.
        language = language() ?: tag?.language,
        type = tag?.type,
    )
}

/**
 * Collapses tracks that point at the same file. A video that advertises one track twice — once as
 * a URL and once as the event that hosts it — would otherwise hand the player two identical
 * subtitle configurations, and ExoPlayer shows both as selectable tracks.
 */
fun mergeCaptionTracks(vararg lists: List<CaptionTrack>): List<CaptionTrack> = lists.flatMap { it }.distinctBy { it.url }
