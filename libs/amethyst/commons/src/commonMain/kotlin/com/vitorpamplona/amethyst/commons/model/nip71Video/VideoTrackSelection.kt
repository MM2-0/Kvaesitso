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

import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoMeta
import com.vitorpamplona.quartz.nip71Video.tags.HlsImetaTag

/**
 * Picks the `imeta` entry a NIP-71 event's player should load.
 *
 * An HLS video event carries a whole ladder in its tags: one imeta for the **master playlist**
 * (which enumerates every rendition, so the player can adapt) plus one per **rendition** (a media
 * playlist locked to a single resolution). Rendering `imetaTags()[0]` blindly — what every call
 * site used to do — only works because Amethyst's own publisher happens to emit the master first
 * (see `HlsVideoEventBuilder`). Any client that orders the tags differently pins us to one rung:
 * no adaptive bitrate, and no quality menu either, because a media playlist exposes a single
 * video track and `VideoQualityButton` hides itself below two.
 *
 * The choice, in order:
 * 1. Drop imetas that can't be the video: audio-only tracks (NIP-71 PR #2255 splits audio out so
 *    clients can switch resolution without interrupting sound) and explicit image posters.
 *    If that leaves nothing, fall back to the first imeta — a NIP-71 event asserts its own type,
 *    and the renderers already handle an image-shaped one.
 * 2. Prefer HLS over a progressive file when both are offered: only HLS can adapt.
 * 3. Among the HLS entries, take the largest declared `dim`, earliest tag wins a tie. A master
 *    advertises the top of its own ladder, so it ties for the maximum and wins by position under
 *    the master-first convention. When a publisher omits the master entirely this picks the top
 *    rendition rather than the bottom one, which is the same complaint answered a weaker way.
 * 4. Only when **no** HLS entry declares a `dim` does tag order decide. A missing `dim` is not a
 *    reliable master signal on its own: a ladder-spanning manifest has no single resolution to
 *    declare, but a sloppily-published *rendition* can equally omit one, and preferring the
 *    dim-less entry outright would then pin us to a single low rung — the exact bug this function
 *    exists to fix. Deferring to the largest declared `dim` fails the other way instead: worst
 *    case we select the top rendition and lose adaptation, never the bottom one.
 *
 * Presentation metadata is ladder-wide — every rung shares the poster, the blurhash and the
 * aspect ratio — so anything missing from the chosen entry is filled in from its siblings. That
 * keeps the placeholder and the layout intact when the master turns out to be the bare one.
 */
fun VideoEvent.selectVideoTrack(): VideoMeta? {
    val imetas = imetaTags().flatMap { it.withHlsVariant() }
    if (imetas.size <= 1) return imetas.firstOrNull()

    val candidates = imetas.filter { it.canBeTheVideo() }
    if (candidates.isEmpty()) return imetas.first()

    val hls = candidates.filter { it.isHlsPlaylist() }
    val selected =
        when {
            hls.isEmpty() -> candidates.first()
            // Nothing declares a resolution, so there is no ladder to compare: fall back to tag
            // order, where the master-first convention puts the manifest first.
            hls.none { it.dimension != null } -> hls.first()
            // maxByOrNull keeps the first of equal maxima, which is the master under that same
            // convention, since it advertises the top rung of its own ladder.
            else -> hls.maxByOrNull { it.pixelCount() } ?: hls.first()
        }

    // Presentation metadata is filled from every imeta, not just the playable candidates: a poster
    // is routinely published as its own `image/*` entry, which canBeTheVideo() excludes.
    return selected.withLadderMetadataFrom(imetas)
}

// A publisher that keeps one imeta per video and hangs its adaptive manifest off the
// non-standard `hls` property (divine.video does; see [HlsImetaTag]) describes a ladder NIP-71
// would have written as a second imeta. Expand it into one, listed first so the HLS preference
// below reaches it, and drop the fields that describe the progressive file specifically: `x` and
// `size` are that blob's digest and byte count, and a manifest is neither.
private fun VideoMeta.withHlsVariant(): List<VideoMeta> {
    val manifest = hls?.takeIf { it.isNotBlank() && it != url } ?: return listOf(this)

    return listOf(
        copy(url = manifest, mimeType = HlsImetaTag.MIME_TYPE, hash = null, size = null, hls = null),
        copy(hls = null),
    )
}

// A NIP-71 event asserts it is a video, so an imeta is a candidate unless it says otherwise:
// `audio/*` is a separate track under PR #2255, and `image/*` is a poster. Everything else —
// including the HLS playlist MIMEs, which are neither `video/*` nor an image, and a bare URL with
// no MIME at all — belongs in the player.
//
// The HLS test comes first because two of the four playlist MIMEs this repo recognises are spelled
// `audio/x-mpegurl` and `audio/mpegurl` — legacy aliases naming the *manifest* format, not audio
// content. Reading those as an audio track drops a master out of the ladder, and drops the whole
// event to its poster when every entry is labelled that way.
private fun VideoMeta.canBeTheVideo(): Boolean = (isHlsPlaylist() || !isAudio) && !isPoster()

// Mirrors MediaItemCache.toExoPlayerMimeType: a declared HLS MIME is authoritative, and the
// `.m3u8` fallback is anchored to the path so `video.mp4?ref=a.m3u8` is not mistaken for a
// playlist. Both halves are needed — a BUD-10 blossom playlist is `https://host/<sha256>` with no
// extension, so only its MIME identifies it.
private fun VideoMeta.isHlsPlaylist(): Boolean {
    if (RichTextParser.isHlsMimeType(mimeType)) return true
    // A declared media type is authoritative, but `application/octet-stream` and friends declare
    // nothing — a server default, not a claim about the file — so the extension still gets a say.
    // Without this a `master.m3u8` served as octet-stream loses to a correctly labelled 360p rung.
    val declared = mimeType
    if (declared != null && !declared.isUninformativeMimeType()) return false
    return url.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)
}

private fun String.isUninformativeMimeType(): Boolean =
    isBlank() ||
        equals("application/octet-stream", ignoreCase = true) ||
        equals("binary/octet-stream", ignoreCase = true)

private fun VideoMeta.isPoster(): Boolean = RichTextParser.classifyMedia(url, mimeType) == MediaContentKind.IMAGE

private fun VideoMeta.pixelCount(): Long {
    val dim = dimension ?: return -1L
    return dim.width.toLong() * dim.height.toLong()
}

private fun VideoMeta.withLadderMetadataFrom(ladder: List<VideoMeta>): VideoMeta {
    // Every rung of a ladder is the same footage at a different bitrate, so the poster, the hashes
    // and the aspect ratio are interchangeable. Only fill gaps; never override what was declared.
    if (dimension != null && blurhash != null && thumbhash != null && image.isNotEmpty() && alt != null) return this

    return copy(
        // Deliberately not from the poster: a 16:9 thumbnail on a vertical short would size the
        // player's box at 16:9. Only entries that could be the video describe its shape.
        dimension = dimension ?: ladder.firstOrNull { it.canBeTheVideo() && it.dimension != null }?.dimension,
        blurhash = blurhash ?: ladder.firstNotNullOfOrNull { it.blurhash },
        thumbhash = thumbhash ?: ladder.firstNotNullOfOrNull { it.thumbhash },
        // An `image/*` sibling carries the poster as its own url, not in its `image` list, so fall
        // back to that before giving up — it is what a still-rendering surface actually wants.
        image =
            image.ifEmpty {
                ladder.firstOrNull { it.image.isNotEmpty() }?.image
                    ?: ladder.firstOrNull { it.isPoster() }?.let { listOf(it.url) }
                    ?: emptyList()
            },
        alt = alt ?: ladder.firstNotNullOfOrNull { it.alt },
    )
}
