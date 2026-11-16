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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewMetadataCalculatorTest {
    @Test
    fun `shouldAttempt accepts AVIF`() {
        assertTrue(PreviewMetadataCalculator.shouldAttempt("image/avif"))
    }

    @Test
    fun `shouldAttempt accepts AVIF case-insensitive`() {
        assertTrue(PreviewMetadataCalculator.shouldAttempt("IMAGE/AVIF"))
    }

    @Test
    fun `computeFromBytes for AVIF with empty bytes returns empty PreviewHashes`() {
        // Below API 28 (Robolectric default), or with an empty payload, the AVIF
        // branch must produce a PreviewHashes with no decoded data rather than
        // crashing. The dimPrecomputed value is preserved when available.
        val result =
            PreviewMetadataCalculator.computeFromBytes(
                data = ByteArray(0),
                mimeType = "image/avif",
                dimPrecomputed = null,
            )
        assertEquals(null, result.blurhash)
        assertEquals(null, result.thumbhash)
        assertEquals(null, result.dim)
    }

    @Test
    fun `computeFromBytes for AVIF with malformed bytes does not crash`() {
        // A few random bytes are not a valid AVIF; the decoder should fail
        // gracefully and return an empty PreviewHashes (no exception escapes).
        val result =
            PreviewMetadataCalculator.computeFromBytes(
                data = byteArrayOf(0x00, 0x01, 0x02, 0x03),
                mimeType = "image/avif",
                dimPrecomputed = null,
            )
        assertEquals(null, result.blurhash)
        assertEquals(null, result.thumbhash)
    }
}
