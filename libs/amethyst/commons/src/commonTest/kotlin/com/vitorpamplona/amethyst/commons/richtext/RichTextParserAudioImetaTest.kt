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

import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The imeta facts an audio card shows before the player loads: `size` and `waveform` reach [MediaUrlVideo]. */
class RichTextParserAudioImetaTest {
    private val url =
        "https://npub17u5dneh8qjp43ecfxr6u5e9sjamsmxyuekrg2nlxrrk6nj9rsyrqywt4tp.blossom.band/" +
            "b21c6e2a4d38f2abac617ac6643aba919f271b37c1c539b5643c17753716c506.mp3"

    private fun parse(vararg imeta: String) =
        RichTextParser()
            .parseText(
                "I can simply generate the esoteric beats I want to hear!\n$url",
                ImmutableListOfLists(arrayOf(arrayOf("imeta", "url $url", *imeta))),
                null,
            ).mediaList
            .single()

    @Test
    fun sizeAndMimeReachTheMedia() {
        // The post from the original report.
        val media = parse("x b21c6e2a4d38f2abac617ac6643aba919f271b37c1c539b5643c17753716c506", "size 4992768", "m audio/mpeg")
        assertIs<MediaUrlVideo>(media)
        assertEquals("audio/mpeg", media.mimeType)
        assertEquals(4_992_768L, media.sizeBytes)
        assertNull(media.waveform)
        assertNull(media.durationSeconds)
    }

    @Test
    fun durationReachesTheMedia() {
        val media = parse("m audio/mpeg", "duration 3725.4")
        assertIs<MediaUrlVideo>(media)
        assertEquals(3725.4, media.durationSeconds)
    }

    @Test
    fun waveformReachesTheMedia() {
        val media = parse("m audio/mpeg", "waveform 0 7 35 100 42")
        assertIs<MediaUrlVideo>(media)
        assertEquals(listOf(0f, 7f, 35f, 100f, 42f), media.waveform)
    }
}
