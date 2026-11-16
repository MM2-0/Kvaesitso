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

import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentProof
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentRequired
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** Result of a BUD-06 `HEAD /upload` or `HEAD /media` preflight. */
data class BlossomPreflightResult(
    val accepted: Boolean,
    val status: Int,
    val reason: String? = null,
)

/**
 * Blossom HTTP client for JVM consumers (desktop + CLI + Android's
 * shared logic). Owns no global state — pass a configured [OkHttpClient] (e.g.
 * desktop's per-URL role-based client) for proxying /
 * connection pooling. The default constructor uses a fresh OkHttpClient — fine
 * for one-shot uses such as the CLI.
 *
 * Covers BUD-01 (download), BUD-02 (upload/list/delete), BUD-04 (mirror),
 * BUD-05 (media), BUD-06 (preflight), BUD-07 (402), and BUD-09 (report). Every
 * optional endpoint degrades gracefully so callers can fan out across servers of
 * varying capability.
 */
open class BlossomClient(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
) : BlossomBlobClient {
    /** [onProgress] follows the bytes sent, restarting from zero if OkHttp retries the request. */
    open suspend fun upload(
        file: File,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): BlossomUploadResult = putBlob(BlossomServerUrl.upload(serverBaseUrl), fileBody(file, contentType, onProgress), serverBaseUrl, authHeader)

    /**
     * Upload [file], naming its [sha256] up front as `X-SHA-256` and `Content-Digest` (the hex hash,
     * as zsp sends it). cdn.zapstore.dev refuses an upload without `Content-Digest`.
     */
    open suspend fun upload(
        file: File,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
        sha256: String,
    ): BlossomUploadResult =
        putBlob(
            BlossomServerUrl.upload(serverBaseUrl),
            fileBody(file, contentType),
            serverBaseUrl,
            authHeader,
            mapOf(BlossomServerUrl.X_SHA_256_HEADER to sha256, CONTENT_DIGEST_HEADER to sha256),
        )

    /**
     * Upload raw bytes (e.g. encrypted blobs) to a Blossom server. [onProgress] follows the bytes
     * sent.
     */
    open suspend fun upload(
        bytes: ByteArray,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): BlossomUploadResult {
        val body =
            if (onProgress == null) {
                bytes.toRequestBody(contentType.toMediaType())
            } else {
                ReopenableStreamBody(contentType.toMediaType(), bytes.size.toLong(), onProgress) { bytes.inputStream() }
            }
        return putBlob(BlossomServerUrl.upload(serverBaseUrl), body, serverBaseUrl, authHeader)
    }

    /**
     * BUD-05 media-optimization upload: `PUT /media`. The server MAY transform the
     * blob, so the returned descriptor's `sha256` is the *optimized* hash and `ox`
     * the original. Requires a `t=media` auth token.
     */
    open suspend fun media(
        file: File,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
    ): BlossomUploadResult = putBlob(BlossomServerUrl.media(serverBaseUrl), fileBody(file, contentType), serverBaseUrl, authHeader)

    open suspend fun media(
        bytes: ByteArray,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
    ): BlossomUploadResult = putBlob(BlossomServerUrl.media(serverBaseUrl), bytes.toRequestBody(contentType.toMediaType()), serverBaseUrl, authHeader)

    /**
     * BUD-04 mirror: ask [serverBaseUrl] to fetch and store the blob already at
     * [sourceUrl]. The server verifies the downloaded bytes hash to the `x` tag in
     * the (upload) auth token. Returns the mirrored blob's descriptor.
     *
     * Throws [BlossomMirrorUnsupportedException] when the server has no `/mirror`
     * endpoint (HTTP 404/405/501) so the caller can tell "can't mirror here" apart
     * from "mirror failed" — see [mirrorOrUpload] for the download-and-upload fallback.
     */
    open suspend fun mirror(
        sourceUrl: String,
        serverBaseUrl: String,
        authHeader: String?,
        paymentProof: BlossomPaymentProof? = null,
    ): BlossomUploadResult =
        withContext(Dispatchers.IO) {
            val body = JsonMapper.toJson(MirrorRequest(sourceUrl)).toRequestBody("application/json".toMediaType())
            val request =
                Request
                    .Builder()
                    .url(BlossomServerUrl.mirror(serverBaseUrl))
                    .apply {
                        authHeader?.let { addHeader("Authorization", it) }
                        paymentProof?.headers()?.forEach { (name, value) -> addHeader(name, value) }
                    }.put(body)
                    .build()
            okHttpClient.newCall(request).executeAsync().use { response ->
                if (response.code in MIRROR_UNSUPPORTED_CODES) {
                    throw BlossomMirrorUnsupportedException(serverBaseUrl, response.code)
                }
                parseDescriptor(response, serverBaseUrl)
            }
        }

    /**
     * Mirror [sourceUrl] to [serverBaseUrl], falling back to a direct upload when the
     * server doesn't implement BUD-04 `/mirror`. First tries [mirror]; if that reports
     * [BlossomMirrorUnsupportedException], the blob is downloaded here, verified against
     * [expectedHash], then re-uploaded with [BlossomServerUrl.UPLOAD_PATH] — so a file
     * still lands on servers of every capability. Payment (`402`) and every other
     * failure propagate unchanged from [mirror].
     *
     * The downloaded bytes are hash-checked before re-upload: a Blossom server is
     * untrusted and could return substituted content, and the same `t=upload` auth
     * (whose `x` tag is the expected hash) is reused for the fallback `PUT /upload`.
     */
    override suspend fun mirrorOrUpload(
        sourceUrl: String,
        expectedHash: HexKey,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
        paymentProof: BlossomPaymentProof?,
    ): BlossomUploadResult =
        try {
            mirror(sourceUrl, serverBaseUrl, authHeader, paymentProof)
        } catch (e: BlossomMirrorUnsupportedException) {
            val bytes =
                download(sourceUrl)
                    ?: throw RuntimeException("Could not download $sourceUrl to upload to $serverBaseUrl")
            val actualHash = sha256(bytes).toHexKey()
            if (actualHash != expectedHash) {
                throw RuntimeException("$sourceUrl returned content ($actualHash) that does not match the expected $expectedHash")
            }
            upload(bytes, contentType, serverBaseUrl, authHeader)
        }

    /**
     * BUD-02 list: `GET /list/<pubkey>`. Returns the pubkey's blob descriptors on
     * this server (may be empty; servers MAY not implement it). [authHeader] is a
     * `t=list` token — some servers require it, others allow anonymous listing.
     */
    override suspend fun list(
        serverBaseUrl: String,
        pubkey: HexKey,
        authHeader: String?,
    ): List<BlossomUploadResult> =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(BlossomServerUrl.list(serverBaseUrl, pubkey))
                    .apply { authHeader?.let { addHeader("Authorization", it) } }
                    .get()
                    .build()
            okHttpClient.newCall(request).executeAsync().use { response ->
                check402(response, serverBaseUrl)
                if (!response.isSuccessful) {
                    val reason = response.headers[BlossomServerUrl.REASON_HEADER] ?: response.code.toString()
                    throw RuntimeException("List failed ($serverBaseUrl): $reason")
                }
                val body = response.body.string().ifBlank { "[]" }
                JsonMapper.fromJson<List<BlossomUploadResult>>(body)
            }
        }

    /**
     * BUD-02 delete: `DELETE /<sha256>[.ext]`. [authHeader] is a `t=delete` token
     * scoped to the hash (and ideally to this server). Returns true on 2xx.
     */
    override suspend fun delete(
        hash: HexKey,
        serverBaseUrl: String,
        authHeader: String?,
        extension: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(BlossomServerUrl.blob(serverBaseUrl, hash, extension))
                    .apply { authHeader?.let { addHeader("Authorization", it) } }
                    .delete()
                    .build()
            okHttpClient.newCall(request).executeAsync().use { it.isSuccessful }
        }

    /**
     * The blob [put] stores on [serverBaseUrl], or, when the server refuses the upload, the blob it
     * already holds under [hash]: a server may answer an upload of a blob it has with an error (a
     * 409) instead of its descriptor, as BUD-02 asks. A blob under that exact hash is these bytes, so
     * the upload is done; anything else fails as it was.
     *
     * A refusal that names some other blob is not that: nostr.download refuses an image it finds
     * perceptually identical to one it holds (409 with `X-Identical-Media` naming that blob), which
     * is different bytes under a different hash. That is not this upload: it fails as a
     * [BlossomIdenticalMediaException], and the uploaders link to that copy when it checks out
     * (it hashes to its name and has this picture's size), or move on to their next server.
     *
     * Only for an upload stored as sent (BUD-02 `/upload`): a BUD-05 `/media` upload may be
     * transformed, so the original hash says nothing about what the server would have kept.
     */
    suspend fun uploadOrHeld(
        hash: HexKey,
        size: Long,
        contentType: String?,
        serverBaseUrl: String,
        put: suspend () -> BlossomUploadResult,
    ): BlossomUploadResult =
        try {
            put()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!has(hash, serverBaseUrl)) throw e
            Log.d(TAG) { "$serverBaseUrl refused $hash, which it already holds: ${e.message}" }
            heldDescriptor(hash, size, contentType, serverBaseUrl)
        }

    /**
     * BUD-01 HEAD probe: does [serverBaseUrl] hold [hash]? A cheap "which server
     * has which blob" check that needs no auth on most servers.
     */
    override suspend fun has(
        hash: HexKey,
        serverBaseUrl: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(BlossomServerUrl.blob(serverBaseUrl, hash))
                    .head()
                    .build()
            try {
                okHttpClient.newCall(request).executeAsync().use { it.isSuccessful }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }

    /**
     * BUD-06 preflight: `HEAD /upload` (or `/media` when [media] is true). A 200
     * means the server would accept the blob; any other status carries an optional
     * `X-Reason`. Per spec this is only a hint — never gate an upload hard on it.
     */
    open suspend fun preflight(
        hash: HexKey,
        size: Long,
        contentType: String,
        serverBaseUrl: String,
        authHeader: String?,
        media: Boolean = false,
    ): BlossomPreflightResult =
        withContext(Dispatchers.IO) {
            val endpoint = if (media) BlossomServerUrl.media(serverBaseUrl) else BlossomServerUrl.upload(serverBaseUrl)
            val request =
                Request
                    .Builder()
                    .url(endpoint)
                    .head()
                    .addHeader(BlossomServerUrl.X_SHA_256_HEADER, hash)
                    .addHeader(BlossomServerUrl.X_CONTENT_LENGTH_HEADER, size.toString())
                    .addHeader(BlossomServerUrl.X_CONTENT_TYPE_HEADER, contentType)
                    .apply { authHeader?.let { addHeader("Authorization", it) } }
                    .build()
            okHttpClient.newCall(request).executeAsync().use { response ->
                BlossomPreflightResult(
                    accepted = response.isSuccessful,
                    status = response.code,
                    reason = response.headers[BlossomServerUrl.REASON_HEADER],
                )
            }
        }

    /**
     * BUD-09 report: `PUT /report` with a signed NIP-56 (kind 1984) report event as
     * the JSON body. Returns true on 2xx.
     */
    override suspend fun report(
        serverBaseUrl: String,
        reportEventJson: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(BlossomServerUrl.report(serverBaseUrl))
                    .put(reportEventJson.toRequestBody("application/json".toMediaType()))
                    .build()
            okHttpClient.newCall(request).executeAsync().use { it.isSuccessful }
        }

    /**
     * Download a blob from an absolute URL — typically a Blossom GET endpoint
     * `<server>/<sha256>`. Returns the raw bytes, or `null` when the server
     * responds with a non-2xx status. Connection-level failures (DNS, refused,
     * timeout) propagate as [java.io.IOException] so the caller can try the next
     * server.
     *
     * This does NOT verify the blob's hash — content-addressed verification is
     * the caller's responsibility (see quartz `StaticSiteResolver.verify`), since
     * a Blossom server is untrusted and may return a substituted blob.
     */
    open suspend fun download(url: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            okHttpClient.newCall(request).executeAsync().use { response ->
                if (response.isSuccessful) response.body.bytes() else null
            }
        }

    private fun fileBody(
        file: File,
        contentType: String,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): RequestBody = ReopenableStreamBody(contentType.toMediaType(), file.length(), onProgress) { file.inputStream() }

    private suspend fun putBlob(
        endpoint: String,
        body: RequestBody,
        serverBaseUrl: String,
        authHeader: String?,
        headers: Map<String, String> = emptyMap(),
    ): BlossomUploadResult =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(endpoint)
                    .apply {
                        authHeader?.let { addHeader("Authorization", it) }
                        headers.forEach { (name, value) -> addHeader(name, value) }
                    }.put(body)
                    .build()
            okHttpClient.newCall(request).executeAsync().use { parseDescriptor(it, serverBaseUrl) }
        }

    private fun parseDescriptor(
        response: Response,
        serverBaseUrl: String,
    ): BlossomUploadResult {
        check402(response, serverBaseUrl)
        BlossomIdenticalMediaException.existingHashOf(response.code, response.headers[BlossomIdenticalMediaException.IDENTICAL_MEDIA_HEADER])?.let {
            throw BlossomIdenticalMediaException(serverBaseUrl, it, response.headers[BlossomServerUrl.REASON_HEADER])
        }
        if (!response.isSuccessful) {
            val reason = response.headers[BlossomServerUrl.REASON_HEADER] ?: response.code.toString()
            throw RuntimeException("Request failed ($serverBaseUrl): $reason")
        }
        val body = response.body.string().ifBlank { throw RuntimeException("$serverBaseUrl returned no body") }
        return JsonMapper.fromJson<BlossomUploadResult>(body)
    }

    /** Surfaces a BUD-07 `402 Payment Required` as a typed exception the caller can act on. */
    private fun check402(
        response: Response,
        serverBaseUrl: String,
    ) {
        if (response.code == 402) {
            throw BlossomPaymentException(serverBaseUrl, BlossomPaymentRequired.fromHeaders { response.headers[it] })
        }
    }

    @kotlinx.serialization.Serializable
    private data class MirrorRequest(
        val url: String,
    )

    companion object {
        /**
         * `PUT /mirror` statuses that mean the endpoint isn't implemented (so the caller
         * should fall back to a direct upload) rather than a mirror the server rejected.
         */
        private val MIRROR_UNSUPPORTED_CODES = setOf(404, 405, 501)

        private const val CONTENT_DIGEST_HEADER = "Content-Digest"

        private const val TAG = "BlossomClient"

        /** What [uploadOrHeld] reports for a blob [serverBaseUrl] already holds under [hash]. */
        fun heldDescriptor(
            hash: HexKey,
            size: Long,
            contentType: String?,
            serverBaseUrl: String,
            extension: String = "",
        ) = BlossomUploadResult(url = BlossomServerUrl.blob(serverBaseUrl, hash, extension), sha256 = hash, size = size, type = contentType)

        /**
         * The file extension a blob URL carries for [contentType] (`image/jpeg` -> `jpg`), or "" for
         * other types. Clients that tell images from links by the URL's ending need one.
         */
        fun extensionFor(contentType: String?): String =
            when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
                "image/jpeg", "image/jpg" -> "jpg"
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                "image/avif" -> "avif"
                else -> ""
            }
    }
}
