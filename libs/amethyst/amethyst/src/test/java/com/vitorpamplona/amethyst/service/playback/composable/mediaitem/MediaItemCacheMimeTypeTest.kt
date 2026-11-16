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

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaItemCacheMimeTypeTest {
    @Test
    fun appleHlsPlaylistMimeIsNormalizedForExoPlayer() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            MediaItemCache.toExoPlayerMimeType("application/vnd.apple.mpegurl"),
        )
    }

    @Test
    fun xMpegUrlVariantsNormalize() {
        assertEquals(MimeTypes.APPLICATION_M3U8, MediaItemCache.toExoPlayerMimeType("application/x-mpegurl"))
        assertEquals(MimeTypes.APPLICATION_M3U8, MediaItemCache.toExoPlayerMimeType("APPLICATION/X-MPEGURL"))
        assertEquals(MimeTypes.APPLICATION_M3U8, MediaItemCache.toExoPlayerMimeType("audio/x-mpegurl"))
        assertEquals(MimeTypes.APPLICATION_M3U8, MediaItemCache.toExoPlayerMimeType("audio/mpegurl"))
    }

    @Test
    fun nonHlsMimeIsForwardedUnchanged() {
        assertEquals("video/mp4", MediaItemCache.toExoPlayerMimeType("video/mp4"))
    }

    @Test
    fun nullOrBlankYieldsNull() {
        assertNull(MediaItemCache.toExoPlayerMimeType(null))
        assertNull(MediaItemCache.toExoPlayerMimeType(""))
        assertNull(MediaItemCache.toExoPlayerMimeType("   "))
    }

    @Test
    fun bareBlossomM3u8UriInfersHlsWhenMimeIsMissing() {
        val uri = "blossom:ce7cad1ad75f26b5ebbd72d1048cb627a1d34529a0eeea087454c96aad8fc3f4.m3u8?xs=cdn.hzrd149.com"
        assertEquals(MimeTypes.APPLICATION_M3U8, MediaItemCache.toExoPlayerMimeType(null, uri))
        assertEquals(MimeTypes.APPLICATION_M3U8, MediaItemCache.toExoPlayerMimeType("", uri))
    }

    @Test
    fun httpsM3u8UriInfersHlsWhenMimeIsMissing() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            MediaItemCache.toExoPlayerMimeType(null, "https://cdn.example.com/video/master.m3u8"),
        )
    }

    @Test
    fun nonHlsUriYieldsNullWhenMimeIsMissing() {
        assertNull(MediaItemCache.toExoPlayerMimeType(null, "https://cdn.example.com/video.mp4"))
    }

    @Test
    fun m3u8InQueryOrFragmentDoesNotMisrouteMp4Playback() {
        assertNull(MediaItemCache.toExoPlayerMimeType(null, "https://cdn.example.com/video.mp4?ref=other.m3u8"))
        assertNull(MediaItemCache.toExoPlayerMimeType(null, "https://cdn.example.com/video.mp4#section=a.m3u8"))
    }

    @Test
    fun httpsM3u8UriWithQueryStringStillInfersHls() {
        assertEquals(
            MimeTypes.APPLICATION_M3U8,
            MediaItemCache.toExoPlayerMimeType(null, "https://cdn.example.com/master.m3u8?token=abc"),
        )
    }

    @Test
    fun imetaMimeTakesPrecedenceOverUriInference() {
        assertEquals(
            "video/mp4",
            MediaItemCache.toExoPlayerMimeType("video/mp4", "https://cdn.example.com/playlist.m3u8"),
        )
    }
}
