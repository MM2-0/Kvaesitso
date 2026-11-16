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
package com.vitorpamplona.amethyst.ui.actions.uploads

import android.net.Uri
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedMediaTest {
    private fun media(mimeType: String?): SelectedMedia = SelectedMedia(mockk<Uri>(relaxed = true), mimeType)

    @Test
    fun `isCompressible true for video`() {
        assertTrue(media("video/mp4").isCompressible())
        assertTrue(media("VIDEO/MP4").isCompressible())
    }

    @Test
    fun `isCompressible true for jpeg and png`() {
        assertTrue(media("image/jpeg").isCompressible())
        assertTrue(media("image/png").isCompressible())
    }

    @Test
    fun `isCompressible false for AVIF`() {
        assertFalse(media("image/avif").isCompressible())
        assertFalse(media("IMAGE/AVIF").isCompressible())
    }

    @Test
    fun `isCompressible false for GIF and SVG`() {
        assertFalse(media("image/gif").isCompressible())
        assertFalse(media("image/svg+xml").isCompressible())
    }

    @Test
    fun `isCompressible false for null mime`() {
        assertFalse(media(null).isCompressible())
    }

    @Test
    fun `isCompressible false for non-media types`() {
        assertFalse(media("application/pdf").isCompressible())
        assertFalse(media("audio/mpeg").isCompressible())
    }
}
