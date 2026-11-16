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

import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The files one launch serves on its origin, as [com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer]
 * reads them: an event's Blossom blobs ([NappletBlobStore]) or an unpacked webxdc archive.
 */
interface NappletFiles {
    /** Whether [requestPath] can be served now, warming whatever it needs. */
    suspend fun isAvailable(requestPath: String): Boolean

    /** The file serving [requestPath], or null when there is none or it couldn't be had. */
    suspend fun find(requestPath: String): NappletBlobStore.Blob?

    /**
     * Whether the launch names [requestPath], found or not: a named file that couldn't be read is an
     * error, never the home page in its place.
     */
    fun isListed(requestPath: String): Boolean

    /** The launch ended: stop any download it started, so a closed app stops costing data. */
    fun close() {}

    /**
     * The one large download this launch is waiting on, while it runs (a webxdc archive), for the
     * loading screen's bar. Null otherwise: blobs are many small files, each quick.
     */
    val download: StateFlow<NappletDownloadProgress?> get() = NO_DOWNLOAD
}

private val NO_DOWNLOAD: StateFlow<NappletDownloadProgress?> = MutableStateFlow(null)
