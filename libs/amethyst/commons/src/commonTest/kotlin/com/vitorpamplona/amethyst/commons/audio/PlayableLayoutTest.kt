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

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayableLayoutTest {
    private val blossomMp3 =
        "https://npub17u5dneh8qjp43ecfxr6u5e9sjamsmxyuekrg2nlxrrk6nj9rsyrqywt4tp.blossom.band/" +
            "b21c6e2a4d38f2abac617ac6643aba919f271b37c1c539b5643c17753716c506.mp3"

    @Test
    fun declaredAudioWithoutArtworkIsTheWaveform() {
        // The post that started this: imeta `m audio/mpeg`, no waveform, no image.
        assertEquals(PlayableLayout.AUDIO_WAVEFORM, playableLayout("audio/mpeg", blossomMp3, hasArtwork = false))
    }

    @Test
    fun declaredAudioWithArtworkIsTheCover() {
        assertEquals(PlayableLayout.AUDIO_COVER, playableLayout("audio/mpeg", blossomMp3, hasArtwork = true))
    }

    @Test
    fun audioExtensionDecidesWithoutAMime() {
        assertEquals(PlayableLayout.AUDIO_WAVEFORM, playableLayout(null, "https://x.com/a.flac", hasArtwork = false))
        assertEquals(PlayableLayout.AUDIO_WAVEFORM, playableLayout(null, "https://x.com/a.Mp3?dl=1", hasArtwork = false))
    }

    @Test
    fun blossomMpgaIsAudioWithoutAMime() {
        assertEquals(true, declaredAsAudio(null, "https://cdn.test/ed13.mpga"))
        assertEquals(PlayableLayout.AUDIO_WAVEFORM, playableLayout(null, "https://cdn.test/ed13.mpga", hasArtwork = false))
    }

    @Test
    fun declaredMimeBeatsTheExtension() {
        assertEquals(PlayableLayout.VIDEO, playableLayout("video/mp4", "https://x.com/a.mp3", hasArtwork = false))
        assertEquals(PlayableLayout.AUDIO_WAVEFORM, playableLayout("audio/mp4", "https://x.com/a.mp4", hasArtwork = false))
    }

    @Test
    fun bareSubtypeMimeIsRepaired() {
        assertEquals(PlayableLayout.AUDIO_WAVEFORM, playableLayout("mp3", "https://x.com/abc", hasArtwork = false))
    }

    @Test
    fun videoIsVideo() {
        assertEquals(PlayableLayout.VIDEO, playableLayout("video/webm", "https://x.com/a", hasArtwork = false))
        assertEquals(PlayableLayout.VIDEO, playableLayout(null, "https://x.com/a.mov", hasArtwork = true))
    }

    @Test
    fun playlistsAreUndecided() {
        assertEquals(PlayableLayout.UNDECIDED, playableLayout(null, "https://x.com/live.m3u8", hasArtwork = false))
        assertEquals(PlayableLayout.UNDECIDED, playableLayout("application/x-mpegurl", "https://x.com/live", hasArtwork = false))
        // Starts with `audio/` but is a playlist, which can carry video.
        assertEquals(PlayableLayout.UNDECIDED, playableLayout("audio/mpegurl", "https://x.com/live", hasArtwork = false))
    }

    @Test
    fun noMimeAndNoExtensionIsUndecided() {
        assertEquals(PlayableLayout.UNDECIDED, playableLayout(null, "https://blossom.example/b21c6e2a4d38f2ab", hasArtwork = false))
    }

    @Test
    fun probeOverridesTheDeclaration() {
        assertEquals(PlayableLayout.AUDIO_WAVEFORM, playableLayout(null, "https://x.com/live.m3u8", hasArtwork = false, probedAudioOnly = true))
        assertEquals(PlayableLayout.VIDEO, playableLayout(null, "https://x.com/live.m3u8", hasArtwork = false, probedAudioOnly = false))
        // An MPEG video mislabelled `audio/mpeg` moves to the video player once its tracks are known.
        assertEquals(PlayableLayout.VIDEO, playableLayout("audio/mpeg", "https://x.com/a.mpg", hasArtwork = false, probedAudioOnly = false))
    }

    @Test
    fun formatLabels() {
        assertEquals("MP3", mediaFormatLabel("audio/mpeg", blossomMp3))
        assertEquals("FLAC", mediaFormatLabel(null, "https://x.com/a.flac?dl=1"))
        assertEquals("M4A", mediaFormatLabel("audio/mp4", "https://x.com/abc"))
        assertEquals("HLS", mediaFormatLabel("audio/mpegurl", "https://x.com/live"))
        assertEquals("HLS", mediaFormatLabel(null, "https://x.com/live.m3u8"))
        assertEquals(null, mediaFormatLabel(null, "https://blossom.example/b21c6e2a4d38f2ab"))
        // A dot in the host is not an extension.
        assertEquals(null, mediaFormatLabel(null, "https://e.nostr.build/a_x_mp3"))
    }
}
