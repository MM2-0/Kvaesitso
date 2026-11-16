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
package com.vitorpamplona.amethyst.commons.cartridges

import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CartDetectorTest {
    @Test
    fun onlyPngsOfUnknownOrCartSizeAreCandidates() {
        // The one cart seen in the wild: a bare nostr.build link with no imeta.
        assertTrue(CartDetector.mightBeCart("https://image.nostr.build/1f6b.png", null, null))
        assertTrue(CartDetector.mightBeCart("https://x.com/a.PNG?w=1", null, null))
        assertTrue(CartDetector.mightBeCart("https://x.com/a", "image/png", null))
        assertTrue(CartDetector.mightBeCart("https://x.com/a.jpg", null, DimensionTag(160, 205)))

        assertFalse(CartDetector.mightBeCart("https://x.com/a.jpg", null, null))
        assertFalse(CartDetector.mightBeCart("https://x.com/a.png", "image/jpeg", null))
        assertFalse(CartDetector.mightBeCart("https://x.com/a.png", null, DimensionTag(1024, 768)))
    }

    @Test
    fun randomBytesAreNotACartridge() {
        assertFalse(CartDetector.isCartridge(ByteArray(100)))
    }
}
