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
package com.vitorpamplona.amethyst.service.uploads.nip96

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.ServerInfoRetriever
import com.vitorpamplona.amethyst.service.uploads.PreviewMetadataCalculator
import com.vitorpamplona.quartz.nip96FileStorage.info.ServerInfo
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import okhttp3.OkHttpClient
import java.io.IOException

/** Android's extension lookup, ahead of the shared client's own table. */
fun androidExtensionFor(mimeType: String): String? = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)

/** The provider's size for [uri], or null when it has no row, no SIZE column or no value in it. */
private fun ContentResolver.querySize(uri: Uri): Long? =
    runCatching {
        query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && it.moveToFirst() && !it.isNull(sizeIndex)) it.getLong(sizeIndex) else null
        }
    }.getOrNull()

/** Reads [uri] to its end to count its bytes, for a provider that does not report a size. */
private fun ContentResolver.countBytes(uri: Uri): Long? =
    openInputStream(uri)?.use { stream ->
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
        }
        total
    }

private fun fileSize(uri: Uri) = runCatching { uri.toFile().length() }.getOrNull()

/** Uploads a content or file [uri] to the NIP-96 server at [serverBaseUrl]. */
suspend fun Nip96Uploader.upload(
    uri: Uri,
    contentType: String?,
    size: Long?,
    alt: String?,
    sensitiveContent: String?,
    serverBaseUrl: String,
    okHttpClient: (String) -> OkHttpClient,
    onProgress: (percentage: Float) -> Unit,
    httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
    context: Context,
    onUploadProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
): MediaUploadResult =
    upload(
        uri,
        contentType,
        size,
        alt,
        sensitiveContent,
        ServerInfoRetriever().loadInfo(serverBaseUrl, okHttpClient),
        okHttpClient,
        onProgress,
        httpAuth,
        context,
        onUploadProgress,
    )

/**
 * Uploads a content or file [uri] to [server], adding the placeholder hashes (blurhash, thumbhash,
 * dimensions) computed locally to what the server reports.
 */
suspend fun Nip96Uploader.upload(
    uri: Uri,
    contentType: String?,
    size: Long?,
    alt: String?,
    sensitiveContent: String?,
    server: ServerInfo,
    okHttpClient: (String) -> OkHttpClient,
    onProgress: (percentage: Float) -> Unit,
    httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent?,
    context: Context,
    onUploadProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
): MediaUploadResult {
    val contentResolver = context.contentResolver
    val myContentType = contentType ?: contentResolver.getType(uri)
    // The body declares this length up front, so it must be exact: a wrong one fails the upload.
    val length = size ?: contentResolver.querySize(uri) ?: fileSize(uri) ?: contentResolver.countBytes(uri) ?: 0

    val localMetadata = PreviewMetadataCalculator.computeFromUri(context, uri, myContentType)
    return upload(
        openStream = { contentResolver.openInputStream(uri) ?: throw IOException("Can't open the image input stream") },
        length = length,
        contentType = myContentType,
        alt = alt,
        sensitiveContent = sensitiveContent,
        server = server,
        okHttpClient = okHttpClient,
        onProgress = onProgress,
        httpAuth = httpAuth,
        extensionFor = ::androidExtensionFor,
        onUploadProgress = onUploadProgress,
    ).mergeLocalMetadata(localMetadata)
}
