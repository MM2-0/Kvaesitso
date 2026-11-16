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
package com.vitorpamplona.amethyst.service.uploads.hls

import android.content.Context
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.upload
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Turns a user-chosen [ServerName] into an [HlsBlobUploader] by adapting the concrete
 * [Nip96Uploader] / [BlossomUploader] to the simpler file+contentType interface the HLS upload
 * pipeline uses. Keeps the pipeline free of direct Amethyst/account wiring so it stays
 * unit-testable.
 *
 * HLS uploads get a dedicated OkHttp client derived from the shared upload client: write and
 * read timeouts are disabled so a slow rendition trickling through at a few hundred KB/s is
 * not killed mid-stream, and server-side hashing/scanning that blocks the response for minutes
 * does not fire the read timeout while the request is still in flight. A generous per-call
 * timeout remains in place as a hard cap so a silently dead connection eventually errors out.
 */
object HlsBlobUploaderFactory {
    private const val CALL_TIMEOUT_MINUTES = 15L

    private fun okHttpClientForHlsUploads(serverBaseUrl: String): OkHttpClient =
        Amethyst.instance.roleBasedHttpClientBuilder
            .okHttpClientForUploads(serverBaseUrl)
            .newBuilder()
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()

    fun create(
        server: ServerName,
        account: Account,
        context: Context,
    ): HlsBlobUploader =
        when (server.type) {
            ServerType.Blossom -> {
                blossomAdapter(server.baseUrl, account, context)
            }

            ServerType.NIP96 -> {
                nip96Adapter(server.baseUrl, account, context)
            }

            ServerType.BuzzWorkspace -> {
                throw IllegalArgumentException("HLS publishing does not target a Buzz workspace's media server")
            }
        }

    private fun blossomAdapter(
        serverBaseUrl: String,
        account: Account,
        context: Context,
    ): HlsBlobUploader =
        HlsBlobUploader { file, contentType, onProgress ->
            BlossomUploader().upload(
                uri = file.toUri(),
                contentType = contentType,
                alt = null,
                sensitiveContent = null,
                serverBaseUrl = serverBaseUrl,
                okHttpClient = ::okHttpClientForHlsUploads,
                httpAuth = account::createBlossomUploadAuth,
                context = context,
                onProgress = onProgress,
            )
        }

    private fun nip96Adapter(
        serverBaseUrl: String,
        account: Account,
        context: Context,
    ): HlsBlobUploader =
        HlsBlobUploader { file, contentType, onProgress ->
            val totalBytes = file.length()
            Nip96Uploader().upload(
                uri = file.toUri(),
                contentType = contentType,
                size = totalBytes,
                alt = null,
                sensitiveContent = null,
                serverBaseUrl = serverBaseUrl,
                okHttpClient = ::okHttpClientForHlsUploads,
                onProgress = { percentage ->
                    // Convert NIP-96's 0..1 fraction to the bytes/total shape the HLS
                    // orchestrator expects; clamped so the written count never exceeds the file.
                    val written =
                        (percentage * totalBytes)
                            .toLong()
                            .coerceIn(0L, totalBytes)
                    onProgress(written, totalBytes)
                },
                httpAuth = account::createHTTPAuthorization,
                context = context,
            )
        }
}
