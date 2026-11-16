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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder

/**
 * The network half of sending a custom emoji into a Marmot group: fetch the
 * picture the user's emoji pack points at, and put its ciphertext on a blob host.
 *
 * Headless code encrypts and builds the event; only the HTTP lives behind this
 * port, so the platform decides which client (and which proxy or Tor route)
 * the two requests use.
 */
interface MarmotEmojiMediaPort {
    class Image(
        val bytes: ByteArray,
        /** The `Content-Type` the host answered with, when it gave one. */
        val contentType: String?,
    )

    /** The bytes of a public emoji image, or null when it could not be fetched. */
    suspend fun download(url: String): Image?

    /**
     * Uploads [ciphertext] as an opaque blob and returns the URL the host gave
     * it, or null when there is nowhere to upload to.
     */
    suspend fun uploadCiphertext(ciphertext: ByteArray): String?
}

/**
 * The platform's [MarmotEmojiMediaPort] over [httpClients], or null on a
 * platform that cannot upload yet (custom emoji then go as public URLs).
 */
expect fun marmotEmojiMediaPortOf(
    account: Account,
    httpClients: IRoleBasedHttpClientBuilder,
): MarmotEmojiMediaPort?
