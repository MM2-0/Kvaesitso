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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CartIntegrityTest {
    // The note behind the bug: Amethyst uploaded the original (eb26…); nostr.build answers Amethyst's
    // User-Agent with a re-compressed copy (1224…) whose pixels, and so the game, are the same.
    private val recorded = "eb2648af8fa3debbc2c3c5ba998582f70607463f2a03e9e93213638eee1e9915"
    private val served = "1224e09f07a76134b0c58a9a87ff211ac9660461cc7ddaec7c945ab671ca3ba8"

    @Test
    fun aMatchingFileIsVerifiedWithoutLoadingIt() {
        assertEquals(CartIntegrity.VERIFIED, CartIntegrity.of(recorded, recorded.uppercase()) { error("not needed") })
    }

    @Test
    fun aNoteWithoutAHashHasNothingToCompare() {
        assertEquals(CartIntegrity.UNRECORDED, CartIntegrity.of(served, null) { error("not needed") })
    }

    @Test
    fun aDifferentFileThatIsStillACartPlays() {
        assertEquals(CartIntegrity.DIFFERENT_CART, CartIntegrity.of(served, recorded) { true })
    }

    @Test
    fun aDifferentFileThatIsNotACartIsRefused() {
        assertEquals(CartIntegrity.DIFFERENT_FILE, CartIntegrity.of(served, recorded) { CartDetector.isCartridge(ByteArray(100)) })
    }

    @Test
    fun theWarningNamesTheServer() {
        assertEquals("image.nostr.build", CartPlayerViewModel.hostOf("https://image.nostr.build/1f6b.png?x=1"))
        assertEquals("example.com:8080", CartPlayerViewModel.hostOf("http://user@example.com:8080/a.png"))
        assertFalse(CartPlayerViewModel.hostOf("https://a.b/c").contains('/'))
    }
}
