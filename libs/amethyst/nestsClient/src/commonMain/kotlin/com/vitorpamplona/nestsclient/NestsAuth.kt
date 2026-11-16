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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent

/**
 * Helper for building the `Authorization: Nostr <base64>` header required by
 * nests audio-room HTTP endpoints (NIP-98).
 *
 * The header value binds the signed event to a specific URL + method so the
 * backend can reject replayed or repurposed tokens. We deliberately do NOT
 * cache the signed event — the nests server enforces a short (~60 s) validity
 * window on `created_at`, so callers should build a fresh header per request.
 */
object NestsAuth {
    /**
     * Sign a kind 27235 event for (`url`, `method`) and return it as a
     * ready-to-use `Authorization` header value (`"Nostr <base64-json>"`).
     *
     * Optionally include a payload hash when the request has a body.
     */
    suspend fun header(
        signer: NostrSigner,
        url: String,
        method: String,
        payload: ByteArray? = null,
    ): String {
        val template = HTTPAuthorizationEvent.build(url = url, method = method, file = payload)
        val signed = signer.sign(template)
        return signed.toAuthToken()
    }
}
