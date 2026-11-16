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
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentProof
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentRequired
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult

/**
 * Thrown when a Blossom server answers with `402 Payment Required` (BUD-07). The
 * caller pays [payment] (Cashu or Lightning) and retries the request with the
 * proof attached.
 */
class BlossomPaymentException(
    val server: String,
    val payment: BlossomPaymentRequired,
) : RuntimeException("Payment required by $server: ${payment.reason ?: "402 Payment Required"}")

/**
 * Thrown when a Blossom server refuses an upload because it already holds an image it finds
 * perceptually identical: nostr.download answers 409 with `X-Identical-Media` naming that blob.
 * [existingHash] is a different blob (other bytes, the same picture as far as the server can
 * tell), and the server will not take this one, so the caller either uses that copy or moves on.
 */
class BlossomIdenticalMediaException(
    val server: String,
    val existingHash: HexKey,
    val reason: String?,
) : RuntimeException("$server already holds an identical image, $existingHash" + (reason?.let { ": $it" } ?: "")) {
    companion object {
        const val IDENTICAL_MEDIA_HEADER = "X-Identical-Media"

        /** The blob a refusal names: a 409 whose [header] is a sha256, or null for any other answer. */
        fun existingHashOf(
            status: Int,
            header: String?,
        ): HexKey? {
            if (status != 409) return null
            val hash = header?.trim()?.lowercase() ?: return null
            return hash.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
        }
    }
}

/**
 * Thrown by [BlossomClient.mirror] when a server does not implement the BUD-04
 * `/mirror` endpoint. Blossom has no capability-discovery mechanism (BUD-04 defines
 * none), so the only reliable signal is the status of the `PUT /mirror` itself:
 * `404 Not Found`, `405 Method Not Allowed`, or `501 Not Implemented` mean the
 * endpoint is absent — as opposed to a mirror the server understood but refused
 * (`400`/`403`/`413`/…, which stay a plain [RuntimeException]). Callers can catch
 * this to fall back to a direct download-and-upload (see [BlossomClient.mirrorOrUpload]).
 */
class BlossomMirrorUnsupportedException(
    val server: String,
    val status: Int,
) : RuntimeException("$server does not support the /mirror endpoint (HTTP $status)")

/**
 * The Blossom blob operations the media-server screens need: list, probe, delete, mirror and
 * report. [BlossomClient] is the OkHttp implementation; platforms hand one out per server so
 * proxying, Tor and connection pooling stay theirs.
 */
interface BlossomBlobClient {
    /** BUD-04 `PUT /mirror`, falling back to a download and `PUT /upload` when the server lacks it. */
    suspend fun mirrorOrUpload(
        sourceUrl: String,
        expectedHash: HexKey,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
        paymentProof: BlossomPaymentProof? = null,
    ): BlossomUploadResult

    /** BUD-02 `GET /list/<pubkey>`. */
    suspend fun list(
        serverBaseUrl: String,
        pubkey: HexKey,
        authHeader: String?,
    ): List<BlossomUploadResult>

    /** BUD-02 `DELETE /<sha256>`. Returns true on 2xx. */
    suspend fun delete(
        hash: HexKey,
        serverBaseUrl: String,
        authHeader: String?,
        extension: String = "",
    ): Boolean

    /** BUD-01 `HEAD /<sha256>`: true when the server holds the blob. */
    suspend fun has(
        hash: HexKey,
        serverBaseUrl: String,
    ): Boolean

    /** BUD-09 `PUT /report`. Returns true on 2xx. */
    suspend fun report(
        serverBaseUrl: String,
        reportEventJson: String,
    ): Boolean
}
