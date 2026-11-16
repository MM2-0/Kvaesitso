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
package com.vitorpamplona.amethyst.commons.video

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two cases that motivated replacing the old `.m3u8`-substring predicate are
 * [recognisesAnExtensionlessBlossomPlaylistByMime] and [ignoresM3u8InAQueryString]; the rest pin the
 * behaviour that must not regress while fixing them.
 */
class IsHlsMediaTest {
    @Test
    fun recognisesAnExtensionlessBlossomPlaylistByMime() {
        // BUD-10: the URL is a bare sha256 with no extension, so the mime is the only signal. The
        // old substring predicate answered false here and the item was routed to the disk cache —
        // fatal for a live playlist.
        val blossom = "https://blossom.example.com/b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"

        assertTrue(isHlsMedia(blossom, "application/x-mpegurl"))
        assertTrue(isHlsMedia(blossom, "application/vnd.apple.mpegurl"))
        assertFalse(isHlsMedia(blossom, null), "no mime and no extension leaves nothing to go on")
    }

    @Test
    fun ignoresM3u8InAQueryString() {
        // The other direction: progressive media permanently excluded from the cache because its
        // query string mentioned a playlist.
        assertFalse(isHlsMedia("https://host/video.mp4?ref=a.m3u8", null))
        assertFalse(isHlsMedia("https://host/video.mp4#a.m3u8", null))
    }

    @Test
    fun recognisesAPlainM3u8Path() {
        assertTrue(isHlsMedia("https://host/live.m3u8", null))
        assertTrue(isHlsMedia("https://host/live.M3U8", null))
        assertTrue(isHlsMedia("https://host/live.m3u8?vt=abc", null), "query strings don't hide the path")
    }

    @Test
    fun anExplicitMimeWins() {
        // A non-HLS mime is respected even on an .m3u8 path — the mime is the more specific signal,
        // and toExoPlayerMimeType only consults the path when no mime was supplied.
        assertFalse(isHlsMedia("https://host/odd.m3u8", "video/mp4"))
    }

    @Test
    fun progressiveMediaIsNotHls() {
        assertFalse(isHlsMedia("https://host/video.mp4", null))
        assertFalse(isHlsMedia("https://host/video.mp4", "video/mp4"))
        assertFalse(isHlsMedia("https://host/audio.m4a", "audio/mp4"))
    }
}
