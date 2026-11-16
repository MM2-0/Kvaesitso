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

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder

/**
 * The first [count] bytes of the image at [url], fetched with a Range request through the
 * image client (so it routes the way the user's Tor settings ask for images). Null on any
 * failure or where no HTTP client exists.
 */
expect suspend fun IRoleBasedHttpClientBuilder.fetchMediaPrefix(
    url: String,
    count: Int,
): ByteArray?

/** The whole image at [url] through the image client; null on failure or past [maxBytes]. */
expect suspend fun IRoleBasedHttpClientBuilder.fetchMedia(
    url: String,
    maxBytes: Int,
): ByteArray?
