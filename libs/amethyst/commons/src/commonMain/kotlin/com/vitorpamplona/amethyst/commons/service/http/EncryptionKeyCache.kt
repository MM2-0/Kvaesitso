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
package com.vitorpamplona.amethyst.commons.service.http

import androidx.collection.LruCache
import com.vitorpamplona.crypto.SymmetricCipher
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri

/**
 * Neigther ExoPlayer, nor Coil support passing key and nonce to the Interceptor via
 * Request.tag, which would be the right way to do this.
 *
 * This class serves as a key cache to decrypt the body of HTTP calls that need it.
 *
 * A key registered under a BUD-10 `blossom:` URI is also filed under the blob's sha256, because
 * nobody downloads the URI itself: Coil's `BlossomFetcher`, the cart player and the video player
 * resolve it to a server URL first, and the key has to follow. [get] falls back to that hash when
 * the URL's last path segment is a Blossom blob name (`<sha256>[.ext]`, BUD-01), on any server,
 * since the hash names the encrypted bytes wherever they are served from.
 */
class EncryptionKeyCache {
    val cache = LruCache<String, DecryptInformation>(100)
    private val byBlobHash = LruCache<String, DecryptInformation>(100)

    fun add(
        url: String?,
        decryptInformation: DecryptInformation,
    ) {
        if (url == null) return
        if (cache.get(url) == null) {
            cache.put(url, decryptInformation)
        }
        blossomHash(url)?.let { hash ->
            if (byBlobHash.get(hash) == null) byBlobHash.put(hash, decryptInformation)
        }
    }

    fun add(
        url: String?,
        cipher: SymmetricCipher,
        expectedMimeType: String?,
    ) = add(url, DecryptInformation(cipher, expectedMimeType))

    // Runs for every request on the media clients, nearly all unencrypted: parse the path only
    // while some blossom: key is registered.
    fun get(url: String): DecryptInformation? = cache.get(url) ?: if (byBlobHash.size() == 0) null else blobHashInPath(url)?.let { byBlobHash.get(it) }

    /** Forgets [url]'s key, e.g. when the message that carried it expired (CORD-08). */
    fun remove(url: String) {
        cache.remove(url)
        blossomHash(url)?.let { byBlobHash.remove(it) }
    }

    private fun blossomHash(url: String): String? = if (url.startsWith("blossom:", ignoreCase = true)) BlossomUri.parse(url)?.sha256?.lowercase() else null

    private fun blobHashInPath(url: String): String? {
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return null
        val path = url.substringBefore('#').substringBefore('?')
        if (path.indexOf('/', path.indexOf("//") + 2) < 0) return null
        return BLOB_NAME
            .matchEntire(path.substringAfterLast('/'))
            ?.groupValues
            ?.get(1)
            ?.lowercase()
    }

    private companion object {
        val BLOB_NAME = Regex("^([0-9a-fA-F]{64})(?:\\.[^./]+)?$")
    }
}

class DecryptInformation(
    val cipher: SymmetricCipher,
    val mimeType: String?,
)
