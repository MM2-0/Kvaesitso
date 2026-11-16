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
package com.vitorpamplona.amethyst.commons.service.uploads.nip96

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Nip96UploaderExtensionTest {
    private val uploader = Nip96Uploader()

    @Test
    fun `fallback returns avif for image avif`() {
        assertEquals("avif", uploader.fallbackExtensionForMimeType("image/avif"))
    }

    @Test
    fun `fallback returns avif for image avif case insensitive`() {
        assertEquals("avif", uploader.fallbackExtensionForMimeType("IMAGE/AVIF"))
    }

    @Test
    fun `fallback returns existing HLS mappings`() {
        assertEquals("m3u8", uploader.fallbackExtensionForMimeType("application/vnd.apple.mpegurl"))
        assertEquals("ts", uploader.fallbackExtensionForMimeType("video/mp2t"))
        assertEquals("mp4", uploader.fallbackExtensionForMimeType("video/mp4"))
    }

    @Test
    fun `fallback covers the common media types the JVM has no map for`() {
        assertEquals("jpg", uploader.fallbackExtensionForMimeType("image/jpeg"))
        assertEquals("png", uploader.fallbackExtensionForMimeType("image/png"))
        assertEquals("webm", uploader.fallbackExtensionForMimeType("video/webm"))
        assertEquals("mp3", uploader.fallbackExtensionForMimeType("audio/mpeg"))
    }

    @Test
    fun `fallback returns null for unknown types`() {
        assertNull(uploader.fallbackExtensionForMimeType("application/x-bogus"))
    }
}
