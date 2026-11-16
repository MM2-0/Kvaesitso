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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent

object BlossomAuth {
    /**
     * BUD-11 read auth (`t=get`). Servers that gate downloads (e.g. Buzz's
     * private media relay) require this on `GET /<sha256>`.
     *
     * [servers] adds BUD-11 `server` tags, scoping the token to those hosts.
     * [hash] adds an `x` tag, scoping it to that one blob — pass null to leave
     * it off, which is what makes a token reusable for every blob on the host
     * (derived blobs like `.thumb.jpg` included). BUD-11 allows either for
     * `GET`, but a token that carries `x` is valid *only* for that hash. See
     * [BlossomAuthorizationEvent.createGetAuth].
     */
    suspend fun createGetAuth(
        hash: HexKey?,
        alt: String,
        signer: NostrSigner,
        servers: List<String> = emptyList(),
    ): String = BlossomAuthorizationEvent.createGetAuth(hash, alt, signer, servers).toAuthorizationHeader()

    suspend fun createUploadAuth(
        hash: HexKey,
        size: Long,
        alt: String,
        signer: NostrSigner,
        servers: List<String> = emptyList(),
    ): String = BlossomAuthorizationEvent.createUploadAuth(hash, size, alt, signer, servers).toAuthorizationHeader()

    suspend fun createMediaAuth(
        hash: HexKey,
        size: Long,
        alt: String,
        signer: NostrSigner,
        servers: List<String> = emptyList(),
    ): String = BlossomAuthorizationEvent.createMediaAuth(hash, size, alt, signer, servers).toAuthorizationHeader()

    suspend fun createListAuth(
        alt: String,
        signer: NostrSigner,
        servers: List<String> = emptyList(),
    ): String = BlossomAuthorizationEvent.createListAuth(signer, alt, servers).toAuthorizationHeader()

    suspend fun createDeleteAuth(
        hash: HexKey,
        alt: String,
        signer: NostrSigner,
        servers: List<String> = emptyList(),
    ): String = BlossomAuthorizationEvent.createDeleteAuth(hash, alt, signer, servers).toAuthorizationHeader()

    fun encodeAuthHeader(event: BlossomAuthorizationEvent): String = event.toAuthorizationHeader()
}
