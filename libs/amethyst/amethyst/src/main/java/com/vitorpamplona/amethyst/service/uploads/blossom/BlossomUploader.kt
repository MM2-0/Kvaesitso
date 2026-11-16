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
package com.vitorpamplona.amethyst.service.uploads.blossom

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_delete_with_message
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_to_server_with_message
import com.vitorpamplona.amethyst.commons.service.HttpStatusMessages
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.BlossomIdenticalMediaException
import com.vitorpamplona.amethyst.commons.service.upload.BlossomPaymentException
import com.vitorpamplona.amethyst.commons.service.upload.ReopenableStreamBody
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.commons.service.uploads.PreviewHashes
import com.vitorpamplona.amethyst.commons.service.uploads.extensionFromMimeType
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.service.uploads.PreviewMetadataCalculator
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.crypto.sha2.sha256StreamWithCount
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentRequired
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.File
import java.io.IOException
import java.io.InputStream

class BlossomUploader {
    fun Context.getFileName(uri: Uri): String? =
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> getContentFileName(uri)
            else -> uri.path?.let(::File)?.name
        }

    private fun Context.getContentFileName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                return@use cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
            }
        }.getOrNull()

    /** What an upload of one file needs before the first request: its hash, size, name and preview hashes. */
    class PreparedBlob(
        val hash: HexKey,
        val size: Long,
        val fileName: String?,
        val contentType: String?,
        val localMetadata: PreviewHashes?,
    )

    /**
     * Reads [uri] once to hash it and once to compute its preview hashes. A caller sending the
     * same file to several servers prepares it once and passes the result to each [upload].
     */
    fun prepare(
        uri: Uri,
        contentType: String?,
        context: Context,
    ): PreparedBlob {
        val contentResolver = context.contentResolver
        val myContentType = contentType ?: contentResolver.getType(uri)

        val imageInputStreamForHash = contentResolver.openInputStream(uri)
        checkNotNull(imageInputStreamForHash) { "Can't open the image input stream" }

        val (hash, size) =
            imageInputStreamForHash.use { stream ->
                val (hashBytes, totalBytes) = sha256StreamWithCount(stream)
                hashBytes.toHexKey() to totalBytes
            }

        return PreparedBlob(
            hash = hash,
            size = size,
            fileName = context.getFileName(uri),
            contentType = myContentType,
            localMetadata = PreviewMetadataCalculator.computeFromUri(context, uri, myContentType),
        )
    }

    suspend fun upload(
        uri: Uri,
        contentType: String?,
        alt: String?,
        sensitiveContent: String?,
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        httpAuth: suspend (hash: HexKey, size: Long, alt: String) -> BlossomAuthorizationEvent?,
        context: Context,
        useMediaEndpoint: Boolean = false,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
        prepared: PreparedBlob? = null,
    ): MediaUploadResult {
        val blob = prepared ?: prepare(uri, contentType, context)
        val contentResolver = context.contentResolver

        return upload(
            { contentResolver.openInputStream(uri) ?: throw IOException("Can't open the image input stream") },
            blob.hash,
            blob.size,
            blob.fileName,
            blob.contentType,
            alt,
            sensitiveContent,
            serverBaseUrl,
            okHttpClient,
            httpAuth,
            context,
            useMediaEndpoint,
            onProgress,
        ).mergeLocalMetadata(blob.localMetadata)
    }

    /**
     * Uploads the blob [openStream] reads. It is called once per attempt: OkHttp writes the
     * body again when it retries, so it must return a new stream each time.
     */
    suspend fun upload(
        openStream: () -> InputStream,
        hash: HexKey,
        length: Long,
        baseFileName: String?,
        contentType: String?,
        alt: String?,
        sensitiveContent: String?,
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        httpAuth: suspend (hash: HexKey, size: Long, alt: String) -> BlossomAuthorizationEvent?,
        context: Context,
        useMediaEndpoint: Boolean = false,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): MediaUploadResult {
        val fileName = baseFileName ?: RandomInstance.randomChars(16)
        // BUD-05: /media asks the server to optimize; /upload stores the exact bytes.
        val apiUrl = if (useMediaEndpoint) BlossomServerUrl.media(serverBaseUrl) else BlossomServerUrl.upload(serverBaseUrl)

        val client = okHttpClient(apiUrl)
        val requestBuilder = Request.Builder()

        val requestBody = ReopenableStreamBody(contentType?.toMediaType(), length, onProgress, openStream)

        httpAuth(hash, length, alt?.let { "Uploading $it" } ?: "Uploading $fileName")?.let {
            requestBuilder.addHeader("Authorization", it.toAuthorizationHeader())
        }

        contentType?.let { requestBuilder.addHeader("Content-Type", it) }

        requestBuilder
            .addHeader("Content-Length", length.toString())
            // The blob's hash (BUD-06). Servers that bind the body to the token's `x` tag before
            // reading it — Buzz answers 401 without it — need it up front.
            .addHeader(SHA256_HEADER, hash)
            .url(apiUrl)
            .put(requestBody)

        val request = requestBuilder.build()

        return client.newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                // nostr.download refuses an image it finds identical to one it holds, naming that blob.
                val identicalTo = BlossomIdenticalMediaException.existingHashOf(response.code, response.headers[BlossomIdenticalMediaException.IDENTICAL_MEDIA_HEADER])
                if (response.isSuccessful) {
                    response.body.use { body ->
                        convertToMediaResult(parseResults(body.string()))
                    }
                } else if (!useMediaEndpoint && BlossomClient(client).has(hash, serverBaseUrl)) {
                    // A server may refuse an upload of a blob it already holds instead of answering with
                    // its descriptor. A blob under this exact hash is these bytes, so it is uploaded. (A
                    // refusal naming a perceptually identical, different blob is not; see BlossomIdenticalMediaException.)
                    convertToMediaResult(BlossomClient.heldDescriptor(hash, length, contentType, serverBaseUrl))
                } else if (identicalTo != null) {
                    // A different blob under another hash: the caller decides whether to use that copy.
                    throw BlossomIdenticalMediaException(serverBaseUrl, identicalTo, response.headers[BlossomServerUrl.REASON_HEADER])
                } else if (response.code == 402) {
                    // BUD-07: paid server. Surface the payment challenge so the caller can
                    // tell the user (auto-settlement via the NIP-60 wallet is a follow-up).
                    throw BlossomPaymentException(serverBaseUrl, BlossomPaymentRequired.fromHeaders { response.headers[it] })
                } else {
                    val errorMessage = response.headers.get(BlossomServerUrl.REASON_HEADER)

                    val explanation = HttpStatusMessages.resourceIdFor(response.code)
                    if (errorMessage != null) {
                        throw RuntimeException(loadStringRes(Res.string.failed_to_upload_to_server_with_message, serverBaseUrl.displayUrl(), errorMessage))
                    } else if (explanation != null) {
                        throw RuntimeException(loadStringRes(Res.string.failed_to_upload_to_server_with_message, serverBaseUrl.displayUrl(), loadStringRes(explanation)))
                    } else {
                        throw RuntimeException(loadStringRes(Res.string.failed_to_upload_to_server_with_message, serverBaseUrl.displayUrl(), response.code.toString()))
                    }
                }
            }
        }
    }

    fun convertToMediaResult(result: BlossomUploadResult): MediaUploadResult =
        MediaUploadResult(
            url = result.url,
            type = result.type,
            sha256 = result.sha256,
            size = result.size,
            uploaded = result.uploaded,
            magnet = result.magnet,
            infohash = result.infohash,
            ipfs = result.ipfs,
        )

    fun String.displayUrl() = this.removeSuffix("/").removePrefix("https://")

    suspend fun delete(
        hash: String,
        contentType: String?,
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        httpAuth: (hash: HexKey, alt: String) -> BlossomAuthorizationEvent?,
        context: Context,
    ): Boolean {
        val extension =
            contentType?.let {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(it) ?: extensionFromMimeType(it)
            } ?: ""

        val apiUrl = serverBaseUrl

        val requestBuilder = Request.Builder()

        httpAuth(hash, "Deleting $hash")?.let {
            requestBuilder.addHeader("Authorization", it.toAuthorizationHeader())
        }

        val request =
            requestBuilder
                .url(BlossomServerUrl.blob(apiUrl, hash, extension))
                .delete()
                .build()

        return okHttpClient(apiUrl).newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                if (response.isSuccessful) {
                    true
                } else {
                    val explanation = HttpStatusMessages.resourceIdFor(response.code)
                    if (explanation != null) {
                        throw RuntimeException(loadStringRes(Res.string.failed_to_delete_with_message, loadStringRes(explanation)))
                    } else {
                        throw RuntimeException(loadStringRes(Res.string.failed_to_delete_with_message, response.code))
                    }
                }
            }
        }
    }

    private fun parseResults(body: String): BlossomUploadResult = JsonMapper.fromJson<BlossomUploadResult>(body)
}

private const val SHA256_HEADER = "X-SHA-256"
