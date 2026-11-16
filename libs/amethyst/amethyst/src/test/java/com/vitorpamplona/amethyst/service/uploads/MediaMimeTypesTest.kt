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
package com.vitorpamplona.amethyst.service.uploads

import com.vitorpamplona.amethyst.commons.service.uploads.AVIF_EXTENSION
import com.vitorpamplona.amethyst.commons.service.uploads.AVIF_MIME
import com.vitorpamplona.amethyst.commons.service.uploads.extensionFromMimeType
import com.vitorpamplona.amethyst.commons.service.uploads.isAvif
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMimeTypesTest {
    @Test
    fun `isAvif matches image avif exactly`() {
        assertTrue(isAvif("image/avif"))
    }

    @Test
    fun `isAvif is case insensitive`() {
        assertTrue(isAvif("IMAGE/AVIF"))
        assertTrue(isAvif("Image/Avif"))
    }

    @Test
    fun `isAvif rejects null`() {
        assertFalse(isAvif(null))
    }

    @Test
    fun `isAvif rejects empty string`() {
        assertFalse(isAvif(""))
    }

    @Test
    fun `isAvif rejects image avif-sequence`() {
        // image/avif-sequence is intentionally not handled (not IANA-registered).
        assertFalse(isAvif("image/avif-sequence"))
    }

    @Test
    fun `isAvif rejects unrelated image types`() {
        assertFalse(isAvif("image/jpeg"))
        assertFalse(isAvif("image/png"))
        assertFalse(isAvif("image/webp"))
        assertFalse(isAvif("image/gif"))
    }

    @Test
    fun `isAvif rejects substring containment`() {
        // Defensively guard against future code that might accept partial matches.
        assertFalse(isAvif("image/avif-sequence"))
        assertFalse(isAvif("application/x-image-avif"))
    }

    @Test
    fun `extensionFromMimeType returns avif for image avif`() {
        assertEquals("avif", extensionFromMimeType("image/avif"))
    }

    @Test
    fun `extensionFromMimeType returns null for unknown types`() {
        assertNull(extensionFromMimeType("image/jpeg"))
        assertNull(extensionFromMimeType(null))
    }

    @Test
    fun `AVIF_MIME constant value`() {
        assertEquals("image/avif", AVIF_MIME)
    }

    @Test
    fun `AVIF_EXTENSION constant value`() {
        assertEquals("avif", AVIF_EXTENSION)
    }
}
