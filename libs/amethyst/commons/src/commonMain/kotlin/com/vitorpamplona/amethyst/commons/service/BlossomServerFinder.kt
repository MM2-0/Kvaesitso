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
package com.vitorpamplona.amethyst.commons.service

/**
 * Resolves a BUD-10 `blossom:` URI to the URL of a server that holds the blob, probing the
 * author's (and the viewer's) Blossom servers.
 */
interface BlossomServerFinder {
    /** The server URL already resolved for [blossomUri], without probing. */
    fun cachedServerUrl(blossomUri: String): String?

    /** Probes until a server answers for [blossomUri]; null when none does. */
    suspend fun findServerUrl(blossomUri: String): String?

    /** Resolves nothing: front ends without a Blossom client yet. */
    object None : BlossomServerFinder {
        override fun cachedServerUrl(blossomUri: String): String? = null

        override suspend fun findServerUrl(blossomUri: String): String? = null
    }
}
