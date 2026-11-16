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

/** How a downloaded cart compares with the hash its note recorded. */
enum class CartIntegrity {
    /** The bytes are the file the note recorded. */
    VERIFIED,

    /** The note recorded no hash, so there is nothing to compare. */
    UNRECORDED,

    /**
     * The bytes differ from the recorded file but still load as a cart. Servers do this: nostr.build
     * answers Amethyst's User-Agent with a re-compressed copy (same pixels, so the same game, but a
     * different hash). It plays, with a warning, because the hash no longer proves it is the
     * author's game.
     */
    DIFFERENT_CART,

    /** The bytes differ from the recorded file and are not a cart. */
    DIFFERENT_FILE,
    ;

    companion object {
        fun of(
            actualSha256: String,
            expectedSha256: String?,
            isCart: () -> Boolean,
        ): CartIntegrity =
            when {
                expectedSha256 == null -> UNRECORDED
                actualSha256.equals(expectedSha256, ignoreCase = true) -> VERIFIED
                isCart() -> DIFFERENT_CART
                else -> DIFFERENT_FILE
            }
    }
}
