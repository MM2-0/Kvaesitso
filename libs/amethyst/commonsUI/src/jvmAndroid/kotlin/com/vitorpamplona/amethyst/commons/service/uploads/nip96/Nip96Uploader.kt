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
package com.vitorpamplona.amethyst.commons.service.uploads.nip96

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_delete_with_message
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_to_server_with_message
import com.vitorpamplona.amethyst.commons.service.HttpStatusMessages
import com.vitorpamplona.amethyst.commons.service.upload.ReopenableStreamBody
import com.vitorpamplona.amethyst.commons.service.uploads.AVIF_EXTENSION
import com.vitorpamplona.amethyst.commons.service.uploads.AVIF_MIME
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.stringOrNull
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip96FileStorage.actions.DeleteResult
import com.vitorpamplona.quartz.nip96FileStorage.actions.PartialEvent
import com.vitorpamplona.quartz.nip96FileStorage.actions.UploadResult
import com.vitorpamplona.quartz.nip96FileStorage.info.ServerInfo
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.coroutines.executeAsync
import java.io.File
import java.io.InputStream

/**
 * NIP-96 HTTP file storage client: multipart upload (waiting on the server's processing URL when
 * it has one) and delete. Platforms feed it a stream or a [File]; [extensionFor] lets Android
 * consult its `MimeTypeMap` before the built-in table.
 */
class Nip96Uploader(
    /** How often [waitProcessing] asks the server how far along it is. */
    private val pollIntervalMs: Long = 500,
) {
    suspend fun upload(
        file: File,
        contentType: String?,
        alt: String?,
        sensitiveContent: String?,
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percentage: Float) -> Unit,
        httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
        onUploadProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): MediaUploadResult =
        upload(
            openStream = { file.inputStream() },
            length = file.length(),
            contentType = contentType,
            alt = alt,
            sensitiveContent = sensitiveContent,
            server = ServerInfoRetriever().loadInfo(serverBaseUrl, okHttpClient),
            okHttpClient = okHttpClient,
            onProgress = onProgress,
            httpAuth = httpAuth,
            onUploadProgress = onUploadProgress,
        )

    /**
     * Uploads the file [openStream] reads. It is called once per attempt: OkHttp writes the
     * body again when it retries, so it must return a new stream each time.
     *
     * [onUploadProgress] follows the bytes sent; [onProgress] follows the server's processing
     * of the file afterwards, when it reports any.
     */
    suspend fun upload(
        openStream: () -> InputStream,
        length: Long,
        contentType: String?,
        alt: String?,
        sensitiveContent: String?,
        server: ServerInfo,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percentage: Float) -> Unit,
        httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
        extensionFor: (mimeType: String) -> String? = { null },
        onUploadProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): MediaUploadResult {
        val fileName = RandomInstance.randomChars(16)
        val extension =
            contentType?.let {
                extensionFor(it) ?: fallbackExtensionForMimeType(it)
            } ?: ""

        val client = okHttpClient(server.apiUrl)
        val requestBuilder = Request.Builder()

        val requestBody: RequestBody =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("expiration", "")
                .addFormDataPart("size", length.toString())
                .also { body ->
                    alt?.ifBlank { null }?.let { body.addFormDataPart("alt", it) }
                    sensitiveContent?.let { body.addFormDataPart(ContentWarningTag.TAG_NAME, it) }
                    contentType?.let { body.addFormDataPart("content_type", it) }
                }.addFormDataPart(
                    "file",
                    "$fileName.$extension",
                    ReopenableStreamBody(contentType?.toMediaType(), length, onUploadProgress, openStream),
                ).build()

        httpAuth(server.apiUrl, "POST", null)?.let { requestBuilder.addHeader("Authorization", it.toAuthToken()) }

        requestBuilder
            .url(server.apiUrl)
            .post(requestBody)

        val request = requestBuilder.build()

        return client.newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                if (response.isSuccessful) {
                    response.body.use { body ->
                        val result = JsonMapper.fromJson<UploadResult>(body.string())
                        if (!result.processingUrl.isNullOrBlank()) {
                            waitProcessing(result, okHttpClient, onProgress)
                        } else if (result.status == "success") {
                            val event = result.nip94Event
                            if (event != null) {
                                convertToMediaResult(event)
                            } else {
                                throw RuntimeException(loadStringRes(Res.string.failed_to_upload_to_server_with_message, server.apiUrl.displayUrl(), result.message))
                            }
                        } else {
                            throw RuntimeException(loadStringRes(Res.string.failed_to_upload_to_server_with_message, server.apiUrl.displayUrl(), result.message))
                        }
                    }
                } else {
                    val msg = response.body.string()

                    val errorMessage =
                        try {
                            val tree = Json.parseToJsonElement(msg) as? JsonObject
                            val status = tree?.stringOrNull("status")
                            val message = tree?.stringOrNull("message")
                            if (status == "error" && message != null) {
                                message
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }

                    val explanation = HttpStatusMessages.resourceIdFor(response.code)
                    if (errorMessage != null) {
                        throw RuntimeException(loadStringRes(Res.string.failed_to_upload_to_server_with_message, server.apiUrl.displayUrl(), errorMessage))
                    } else if (explanation != null) {
                        throw RuntimeException(loadStringRes(Res.string.failed_to_upload_to_server_with_message, server.apiUrl.displayUrl(), loadStringRes(explanation)))
                    } else {
                        throw RuntimeException(loadStringRes(Res.string.failed_to_upload_to_server_with_message, server.apiUrl.displayUrl(), response.code.toString()))
                    }
                }
            }
        }
    }

    fun String.displayUrl() = this.removeSuffix("/").removePrefix("https://")

    // Android's MimeTypeMap does not know every MIME we upload (notably HLS playlist types
    // and AVIF on older Android), and the JVM has no such map at all. When the platform has no
    // answer we fall back to a small static table so the multipart filename still carries a real
    // extension — otherwise the server gets "name." and echoes it back, which breaks HLS URL
    // rewriting (and rejects extension-less uploads on some NIP-96 servers).
    internal fun fallbackExtensionForMimeType(mimeType: String): String? =
        when (mimeType.lowercase()) {
            "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/x-mpegurl", "audio/mpegurl" -> "m3u8"
            "video/mp2t" -> "ts"
            "video/iso.segment", "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "audio/mpeg" -> "mp3"
            "audio/mp4", "audio/aac" -> "m4a"
            "audio/ogg" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            AVIF_MIME -> AVIF_EXTENSION
            else -> null
        }

    fun convertToMediaResult(nip96: PartialEvent): MediaUploadResult {
        // Images don't seem to be ready immediately after upload
        val imageUrl = nip96.tags?.firstOrNull { it.size > 1 && it[0] == "url" }?.get(1)
        val remoteMimeType =
            nip96.tags
                ?.firstOrNull { it.size > 1 && it[0] == "m" }
                ?.get(1)
                ?.ifBlank { null }
        val hash =
            nip96.tags
                ?.firstOrNull { it.size > 1 && it[0] == "x" }
                ?.get(1)
                ?.ifBlank { null }
        val dim =
            nip96.tags
                ?.firstOrNull { it.size > 1 && it[0] == "dim" }
                ?.get(1)
                ?.ifBlank { null }
                ?.let { DimensionTag.parse(it) }
        val magnet =
            nip96.tags
                ?.firstOrNull { it.size > 1 && it[0] == "magnet" }
                ?.get(1)
                ?.ifBlank { null }

        return MediaUploadResult(
            url = imageUrl,
            type = remoteMimeType,
            sha256 = hash,
            dimension = dim,
            magnet = magnet,
        )
    }

    suspend fun delete(
        hash: String,
        contentType: String?,
        server: ServerInfo,
        okHttpClient: (String) -> OkHttpClient,
        httpAuth: (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
        extensionFor: (mimeType: String) -> String? = { null },
    ): Boolean {
        val extension =
            contentType?.let { extensionFor(it) ?: fallbackExtensionForMimeType(it) } ?: ""

        val client = okHttpClient(server.apiUrl)

        val requestBuilder = Request.Builder()

        httpAuth(server.apiUrl, "DELETE", null)?.let { requestBuilder.addHeader("Authorization", it.toAuthToken()) }

        val request =
            requestBuilder
                .url(server.apiUrl.removeSuffix("/") + "/$hash.$extension")
                .delete()
                .build()

        return client.newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                if (response.isSuccessful) {
                    val result = JsonMapper.fromJson<DeleteResult>(response.body.string())
                    result.status == "success"
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

    /**
     * Polls the server's processing URL until it reports 100%. Gives up after
     * [MAX_FAILED_POLLS] polls in a row that fail or [MAX_PROCESSING_POLLS] in all, so a server
     * that errors or stalls forever ends the upload instead of holding it open.
     */
    private suspend fun waitProcessing(
        result: UploadResult,
        okHttpClient: (String) -> OkHttpClient,
        onProgress: (percentage: Float) -> Unit,
    ): MediaUploadResult {
        var currentResult = result

        val procUrl = result.processingUrl

        var polls = 0
        var failedPolls = 0
        while (!procUrl.isNullOrBlank() && (currentResult.percentage ?: 100) < 100) {
            if (polls >= MAX_PROCESSING_POLLS || failedPolls >= MAX_FAILED_POLLS) {
                throw RuntimeException("The server did not finish processing the file")
            }
            polls++

            onProgress((currentResult.percentage ?: 100) / 100f)

            val request: Request =
                Request
                    .Builder()
                    .url(procUrl)
                    .build()

            val polled =
                try {
                    okHttpClient(procUrl).newCall(request).executeAsync().use { response ->
                        withContext(Dispatchers.IO) {
                            if (response.isSuccessful) JsonMapper.fromJson<UploadResult>(response.body.string()) else null
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }

            if (polled != null) {
                currentResult = polled
                failedPolls = 0
            } else {
                failedPolls++
            }

            delay(pollIntervalMs)
        }
        onProgress((currentResult.percentage ?: 100) / 100f)

        val nip94 = currentResult.nip94Event

        if (nip94 != null) {
            return convertToMediaResult(nip94)
        } else {
            throw RuntimeException("Error waiting for processing. Final result is unavailable")
        }
    }

    companion object {
        /** Polls in a row that may fail before the upload is given up. */
        const val MAX_FAILED_POLLS = 20

        /** Polls in all: 10 minutes at the default interval, for a long video the server transcodes. */
        const val MAX_PROCESSING_POLLS = 1200
    }
}
