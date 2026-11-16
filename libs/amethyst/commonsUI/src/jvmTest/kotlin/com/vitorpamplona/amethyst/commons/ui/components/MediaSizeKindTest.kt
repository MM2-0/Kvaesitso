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
package com.vitorpamplona.amethyst.commons.ui.components

import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.service.MediaSizeRole
import kotlin.test.Test
import kotlin.test.assertEquals

/** The type word a size label shows, and which HTTP client asks for the size. */
class MediaSizeKindTest {
    @Test
    fun imageKind() {
        assertEquals(MediaKind.IMAGE, MediaUrlImage("https://h/a.jpg").mediaKind())
        assertEquals(MediaSizeRole.IMAGE, MediaKind.IMAGE.role)
    }

    @Test
    fun pdfKind() {
        assertEquals(MediaKind.PDF, MediaUrlPdf("https://h/a.pdf").mediaKind())
        assertEquals(MediaSizeRole.PREVIEW, MediaKind.PDF.role)
    }

    @Test
    fun audioKind() {
        assertEquals(MediaKind.AUDIO, MediaUrlVideo("https://h/a.mp3", mimeType = "audio/mpeg").mediaKind())
        assertEquals(MediaSizeRole.VIDEO, MediaKind.AUDIO.role)
    }

    @Test
    fun videoKind() {
        assertEquals(MediaKind.VIDEO, MediaUrlVideo("https://h/a.mp4").mediaKind())
    }
}
