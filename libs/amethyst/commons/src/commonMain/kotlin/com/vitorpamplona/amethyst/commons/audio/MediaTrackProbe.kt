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
package com.vitorpamplona.amethyst.commons.audio

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What reading a media file's track list found. */
enum class MediaTracks {
    /** Every track is audio: draw an audio card, whatever the URL or MIME type says. */
    AUDIO_ONLY,

    /** At least one video track: draw the video player. */
    HAS_VIDEO,

    /** Not an MP4-family file, or its track list couldn't be read: trust the declaration. */
    UNKNOWN,
}

/**
 * Reads the track list of the MP4-family file at [url] over the video client (so it follows the user's
 * Tor settings for media) and remembers the answer in [MediaTrackProbeCache]. Only MP4-family media is
 * probed ([isMp4FamilyMedia]); anything else is [MediaTracks.UNKNOWN] without a request. iOS has no HTTP
 * client yet and always answers [MediaTracks.UNKNOWN].
 */
suspend fun IRoleBasedHttpClientBuilder.probeMediaTracks(
    url: String,
    mimeType: String?,
): MediaTracks {
    MediaTrackProbeCache.get(url)?.let { return it }
    if (!isMp4FamilyMedia(mimeType, url)) return MediaTracks.UNKNOWN
    MediaTrackProbeCache.begin(url)
    try {
        val result =
            when (readMp4AudioOnly(url)) {
                true -> MediaTracks.AUDIO_ONLY
                false -> MediaTracks.HAS_VIDEO
                null -> MediaTracks.UNKNOWN
            }
        MediaTrackProbeCache.put(url, result)
        return result
    } finally {
        MediaTrackProbeCache.end(url)
    }
}

/** [probeMp4AudioOnly] over this platform's HTTP client: null when it can't tell. */
internal expect suspend fun IRoleBasedHttpClientBuilder.readMp4AudioOnly(url: String): Boolean?

/**
 * Probe answers by URL, so a card scrolled back into view takes its real shape on the first frame instead
 * of starting from the declaration and reflowing. Copy-on-write over a [MutableStateFlow], which makes it
 * safe to read from composition and write from IO threads.
 */
object MediaTrackProbeCache {
    private const val MAX_ENTRIES = 500
    private val entries = MutableStateFlow<Map<String, MediaTracks>>(emptyMap())

    private val running = MutableStateFlow<Set<String>>(emptySet())

    /** URLs whose tracks are being read right now: the media size lookup waits for these instead of asking twice. */
    val inFlight: StateFlow<Set<String>> = running

    internal fun begin(url: String) = running.update { it + url }

    internal fun end(url: String) = running.update { it - url }

    fun get(url: String): MediaTracks? = entries.value[url]

    fun put(
        url: String,
        tracks: MediaTracks,
    ) {
        entries.update { current ->
            val next = LinkedHashMap(current)
            next.remove(url)
            next[url] = tracks
            while (next.size > MAX_ENTRIES) next.remove(next.keys.first())
            next
        }
    }
}
