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
package com.vitorpamplona.amethyst.commons.music

import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MusicTrackMediaTest {
    private fun track(vararg tags: Array<String>) =
        MusicTrackEvent(
            id = "a".repeat(64),
            pubKey = "4be27446dc3be488e08232c99b8f70a8c60832534902d979d9a3f171a0b200b0",
            createdAt = 1791030750,
            tags = arrayOf(arrayOf("d", "track"), *tags),
            content = "",
            sig = "0".repeat(128),
        )

    @Test
    fun mp4InUrlWithoutVideoTagStaysAudioForTheCardToProbe() {
        // Both of these are `.mp4` in `url` with no `format`: Nostria's 1080p music video, and an AAC
        // track Amethyst uploaded to Blossom. Only the file's track list tells them apart, so the event
        // keeps both on the audio path and the card probes the file (see AudioFirstMedia).
        val video = "https://nostr.download/21535ca606b945416afe4e39e46d578290413b393483f0e67be8ec3e3bf78998.mp4"
        val aac = "https://blossom.band/727dbd3c23b2f6c1de63fd87083ecadae5782963bb94cefe81c9f3de361bbee0.mp4"

        assertEquals(MusicTrackMedia.Audio(video, null), track(arrayOf("title", "Aza Song   Podcast 39"), arrayOf("url", video)).media())
        assertEquals(MusicTrackMedia.Audio(aac, null), track(arrayOf("url", aac)).media())
        assertEquals(MusicTrackMedia.Audio("https://x.com/clip.mov", null), track(arrayOf("url", "https://x.com/clip.mov")).media())
    }

    @Test
    fun audioExtensionsStayAudio() {
        listOf("mp3", "mpga", "m4a", "flac", "ogg", "opus", "wav").forEach { ext ->
            val url = "https://example.com/song.$ext"
            assertEquals(MusicTrackMedia.Audio(url, null), track(arrayOf("url", url)).media(), ext)
        }
    }

    @Test
    fun extensionlessUrlStaysAudio() {
        val url = "https://blossom.example/5f0c1a5e8b7c0d3f6a2e9b4c1d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e"
        assertEquals(MusicTrackMedia.Audio(url, null), track(arrayOf("url", url)).media())
    }

    @Test
    fun hlsPlaylistStaysAudio() {
        // A playlist carries audio or video; a music track's `url` is audio unless proven otherwise.
        val url = "https://example.com/stream/track.m3u8"
        assertEquals(MusicTrackMedia.Audio(url, null), track(arrayOf("url", url)).media())
    }

    @Test
    fun webmPlaysAsVideo() {
        val url = "https://example.com/clip.webm"
        assertEquals(MusicTrackMedia.Video(url), track(arrayOf("url", url)).media())
    }

    @Test
    fun formatTagKeepsAnMp4UrlOnTheAudioPath() {
        val url = "https://example.com/song.mp4"
        assertEquals(MusicTrackMedia.Audio(url, "audio/mp4"), track(arrayOf("url", url), arrayOf("format", "mp4")).media())
    }

    @Test
    fun formatTagNamesARealAudioMimeType() {
        val url = "https://blossom.band/abc"
        assertEquals(MusicTrackMedia.Audio(url, "audio/mp4"), track(arrayOf("url", url), arrayOf("format", "m4a")).media())
        assertEquals(MusicTrackMedia.Audio(url, "audio/mpeg"), track(arrayOf("url", url), arrayOf("format", "mp3")).media())
        assertEquals(MusicTrackMedia.Audio(url, "audio/flac"), track(arrayOf("url", url), arrayOf("format", "FLAC")).media())
        // Blossom's name for an `audio/mpeg` upload.
        assertEquals(MusicTrackMedia.Audio(url, "audio/mpeg"), track(arrayOf("url", url), arrayOf("format", "mpga")).media())
    }

    @Test
    fun uploadsGetTheFormatTheirMimeTypeNames() {
        assertEquals("m4a", musicFormatTagFor("audio/mp4"))
        assertEquals("m4a", musicFormatTagFor("video/mp4"))
        assertEquals("m4a", musicFormatTagFor("audio/x-m4a"))
        assertEquals("mp3", musicFormatTagFor("audio/mpeg"))
        assertEquals("flac", musicFormatTagFor("audio/flac; charset=binary"))
        assertEquals(null, musicFormatTagFor("image/png"))
        assertEquals(null, musicFormatTagFor(null))
    }

    @Test
    fun videoTagWinsOverUrl() {
        val event = track(arrayOf("url", "https://example.com/song.mp3"), arrayOf("video", "https://example.com/clip.mp4"))
        assertEquals(MusicTrackMedia.Video("https://example.com/clip.mp4"), event.media())
    }

    @Test
    fun noUrlNoMedia() {
        assertNull(track(arrayOf("title", "Silence")).media())
    }
}
