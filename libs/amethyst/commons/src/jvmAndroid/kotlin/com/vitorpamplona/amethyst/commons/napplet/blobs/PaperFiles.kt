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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer

/**
 * A front page's one file ([com.vitorpamplona.amethyst.commons.napplet.HostProfile.PAPER]): the
 * checked document, held in memory and served at `/index.html` (and `/`). Nothing is fetched and
 * nothing else exists, so every other path is a 404.
 */
class PaperFiles(
    private val html: ByteArray,
) : NappletFiles {
    private fun isPage(requestPath: String) = requestPath == NappletSiteServer.INDEX_PATH || requestPath == "/"

    override suspend fun isAvailable(requestPath: String): Boolean = isPage(requestPath)

    override suspend fun find(requestPath: String): NappletBlobStore.Blob? = if (isPage(requestPath)) NappletBlobStore.Blob(NappletSiteServer.INDEX_PATH, "", "text/html; charset=utf-8", null, html) else null

    override fun isListed(requestPath: String): Boolean = isPage(requestPath)
}
