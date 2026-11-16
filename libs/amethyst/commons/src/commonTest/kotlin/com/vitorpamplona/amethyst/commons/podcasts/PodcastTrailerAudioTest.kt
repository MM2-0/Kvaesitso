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
package com.vitorpamplona.amethyst.commons.podcasts

import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PodcastTrailerAudioTest {
    private fun trailer(vararg tags: Array<String>) =
        Podcasting20TrailerEvent(
            id = "9d3bf72c78a4".padEnd(64, '0'),
            pubKey = "4be27446dc3be488e08232c99b8f70a8c60832534902d979d9a3f171a0b200b0",
            createdAt = 1791913269,
            tags = arrayOf(arrayOf("d", "trailer"), arrayOf("title", "skreenplay 808"), *tags),
            content = "",
            sig = "0".repeat(128),
        )

    @Test
    fun aBlossomMpgaTrailerSaysItsMimeInType() {
        // As served by wss://search.brainstorm.world: the file in `url`, its mime in `type`.
        val url = "https://pyramints.kozow.com/route96-api/ed1353c8d11f0a48cbff43d339a530420997df4b874185c561ad6ff8c6457833.mpga"
        val audio = trailer(arrayOf("url", url), arrayOf("length", "11663813"), arrayOf("type", "audio/mpeg")).trailerAudio()

        assertEquals(url, audio?.url)
        assertEquals("audio/mpeg", audio?.mediaType)
    }

    @Test
    fun anExtensionlessTrailerPlaysAsItsType() {
        val audio = trailer(arrayOf("url", "https://cdn.test/ed13"), arrayOf("type", "audio/mpeg")).trailerAudio()

        assertEquals("https://cdn.test/ed13", audio?.url)
        assertEquals("audio/mpeg", audio?.mediaType)
    }

    @Test
    fun withoutATypeTheExtensionNamesTheMime() {
        assertEquals("audio/mpeg", trailer(arrayOf("url", "https://cdn.test/ed13.mpga")).trailerAudio()?.mediaType)
        assertEquals("audio/mpeg", trailer(arrayOf("url", "https://cdn.test/ed13.mp3"), arrayOf("type", "")).trailerAudio()?.mediaType)
        // A bare subtype is repaired, and a `type` that is no mime at all is ignored.
        assertEquals("audio/mpeg", trailer(arrayOf("url", "https://cdn.test/ed13"), arrayOf("type", "mpga")).trailerAudio()?.mediaType)
        assertNull(trailer(arrayOf("url", "https://cdn.test/ed13"), arrayOf("type", "full")).trailerAudio()?.mediaType)
    }

    @Test
    fun noUrlIsNothingToPlay() {
        assertNull(trailer(arrayOf("type", "audio/mpeg")).trailerAudio())
    }
}
