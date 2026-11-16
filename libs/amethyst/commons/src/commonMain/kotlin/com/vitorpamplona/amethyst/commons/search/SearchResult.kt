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
package com.vitorpamplona.amethyst.commons.search

/**
 * Represents a parsed search result from Bech32/hex input.
 * Shared between Android and Desktop for consistent search behavior.
 */
sealed class SearchResult {
    /**
     * Direct user lookup from npub, nprofile, nsec, or hex pubkey.
     */
    data class UserResult(
        val pubKeyHex: String,
        val displayId: String,
    ) : SearchResult()

    /**
     * Note lookup from note1 or nevent.
     */
    data class NoteResult(
        val noteIdHex: String,
        val displayId: String,
    ) : SearchResult()

    /**
     * Addressable event lookup from naddr.
     */
    data class AddressResult(
        val kind: Int,
        val pubKeyHex: String,
        val dTag: String,
        val displayId: String,
    ) : SearchResult()
}
