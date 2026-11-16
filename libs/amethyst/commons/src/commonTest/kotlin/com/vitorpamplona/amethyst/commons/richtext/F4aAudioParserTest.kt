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

import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class F4aAudioParserTest {
    @Test
    fun detectsF4aByExtension() {
        val url = "https://example.com/audio/track.f4a"
        val state = RichTextParser().parseText(url, EmptyTagList, null)

        val media = state.mediaForPager[url]
        assertTrue(media is MediaUrlVideo, "Expected MediaUrlVideo for .f4a URL")

        val segment = state.paragraphs[0].words[0]
        assertTrue(segment is VideoSegment, "Expected VideoSegment for .f4a URL, got ${segment::class.simpleName}")
        assertEquals(url, segment.segmentText)
    }

    @Test
    fun isVideoUrlHelperMatchesF4aExtension() {
        assertTrue(RichTextParser.isVideoUrl("https://example.com/track.f4a"))
        assertTrue(RichTextParser.isVideoUrl("https://example.com/track.F4A"))
        assertTrue(RichTextParser.isVideoUrl("https://example.com/track.f4a?sig=abc"))
    }

    @Test
    fun f4aMapsToMp4AudioMimeType() {
        assertEquals("audio/mp4", mimeTypeMap["f4a"])
    }
}
