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
package com.vitorpamplona.amethyst.commons.richtext

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RichTextParserAudioUrlTest {
    @Test
    fun mp3IsAudio() {
        assertTrue(RichTextParser.isAudioUrl("https://haven.sdbitcoiners.com/f28a5a2e.mp3"))
    }

    @Test
    fun everyAudioContainerIsAudio() {
        RichTextParser.audioExt.forEach {
            assertTrue(RichTextParser.isAudioUrl("https://example.com/a.$it"), it)
        }
    }

    @Test
    fun blossomMpgaIsAudio() {
        // Blossom servers name an `audio/mpeg` upload `.mpga` (podcast episodes, trailers and tracks
        // as served by wss://search.brainstorm.world, 2026-10).
        val url = "https://blossom.yakihonne.com/3a9b1d2fdab3be3273271918cc641bbdb514b28b25c7b9511b822a5e7f1d2d45.mpga"
        assertTrue(RichTextParser.isAudioUrl(url))
        assertTrue(RichTextParser.isAudioContent(null, url))
        assertEquals("audio/mpeg", mimeTypeMap["mpga"])
        assertEquals("audio/mpeg", normalizeMimeType("mpga"))
    }

    @Test
    fun uppercaseIsAudio() {
        assertTrue(RichTextParser.isAudioUrl("https://example.com/A.MP3"))
    }

    @Test
    fun queryParamsAndFragmentsAreIgnored() {
        assertTrue(RichTextParser.isAudioUrl("https://example.com/a.mp3?x=1"))
        assertTrue(RichTextParser.isAudioUrl("https://example.com/a.mp3#t=10"))
    }

    @Test
    fun videoIsNotAudio() {
        assertFalse(RichTextParser.isAudioUrl("https://example.com/a.mp4"))
        assertFalse(RichTextParser.isAudioUrl("https://example.com/a.webm"))
        assertFalse(RichTextParser.isAudioUrl("https://example.com/a.mov"))
    }

    @Test
    fun hlsPlaylistIsNotAudio() {
        // A .m3u8 carries either, and a live stream is the reason the 16:9 default exists.
        assertFalse(RichTextParser.isAudioUrl("https://example.com/stream.m3u8"))
    }

    @Test
    fun imagesAndUnknownAreNotAudio() {
        assertFalse(RichTextParser.isAudioUrl("https://example.com/a.jpg"))
        assertFalse(RichTextParser.isAudioUrl("https://example.com/nothing"))
    }

    @Test
    fun mimeTypeIsAuthoritativeOverTheUrl() {
        assertTrue(RichTextParser.isAudioContent("audio/mpeg", "https://example.com/download?id=7"))
        assertFalse(RichTextParser.isAudioContent("video/mp4", "https://example.com/a.mp3"))
    }

    @Test
    fun urlExtensionIsTheFallbackWithoutAMimeType() {
        assertTrue(RichTextParser.isAudioContent(null, "https://example.com/a.mp3"))
        assertFalse(RichTextParser.isAudioContent(null, "https://example.com/a.mp4"))
    }

    @Test
    fun theExtensionMustBeIntroducedByADot() {
        // The note that found this: nostr.build hands out an HTML player page whose path ends in
        // `_mp3`, not `.mp3`. Verified with curl -- it answers `Content-Type: text/html`, so
        // routing it to the player can only ever produce a spinner.
        assertFalse(RichTextParser.isAudioUrl("https://e.nostr.build/a_ETvKzX2OdOGmFEp1avRlm5_mp3?t=Aria&by=The+Fishcake"))
        assertNull(RichTextParser.classifyMedia("https://e.nostr.build/a_ETvKzX2OdOGmFEp1avRlm5_mp3?t=Aria&by=The+Fishcake", null))

        // The file that page is about, which does have the dot.
        assertTrue(RichTextParser.isAudioUrl("https://a.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp3"))
    }

    @Test
    fun theVideoPlayerPageIsTheSameTrap() {
        // nostr.build mirrors the scheme for video: `v_<id>_mp4` is an HTML page, and the file it
        // is about lives on v.nostr.build.
        assertFalse(RichTextParser.isVideoUrl("https://e.nostr.build/v_ETvKzX2OdOGmFEp1avRlm5_mp4?t=Clip"))
        assertNull(RichTextParser.classifyMedia("https://e.nostr.build/v_ETvKzX2OdOGmFEp1avRlm5_mp4?t=Clip", null))

        assertTrue(RichTextParser.isVideoUrl("https://v.nostr.build/ETvKzX2OdOGmFEp1avRlm5.mp4"))
    }

    @Test
    fun aSlugEndingInAnExtensionsLettersIsNotMedia() {
        assertFalse(RichTextParser.isAudioUrl("https://example.com/my-thoughts-on-mp3"))
        assertFalse(RichTextParser.isVideoUrl("https://example.com/how-to-rip-a-webm"))
        assertFalse(RichTextParser.isImageUrl("https://example.com/blog/png"))
        assertNull(RichTextParser.classifyMedia("https://example.com/my-thoughts-on-mp3", null))
    }

    @Test
    fun aDotInTheHostIsNotAnExtension() {
        // The last dot of `https://files.mp3/download` sits before a `/`, so it opens a host
        // name, not a file extension.
        assertFalse(RichTextParser.isAudioUrl("https://files.mp3/download"))
        assertFalse(RichTextParser.isAudioUrl("https://example.com/mp3"))
    }

    @Test
    fun mixedCaseExtensionsResolve() {
        // The lower+UPPER doubled lists never covered these; matching case-insensitively does.
        assertTrue(RichTextParser.isAudioUrl("https://example.com/a.Mp3"))
        assertTrue(RichTextParser.isAudioUrl("https://example.com/a.mP3?x=1"))
    }
}
