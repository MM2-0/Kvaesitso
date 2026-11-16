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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotEmojiMediaPort
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * [MarmotEmojiMediaPort] over the account's HTTP clients, so both requests take
 * the route (direct, proxy or Tor) the user configured for images and uploads.
 *
 * The upload follows the same privacy rules as every other Marmot attachment
 * (see CordnMediaService for the reasoning): `application/octet-stream`, no
 * alt text, and the BUD-02 `/upload` endpoint, never `/media`, which would
 * re-encode and so destroy the ciphertext.
 */
class MarmotEmojiMediaService(
    private val account: Account,
    private val httpClients: IRoleBasedHttpClientBuilder,
) : MarmotEmojiMediaPort {
    override suspend fun download(url: String): MarmotEmojiMediaPort.Image? =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            httpClients.okHttpClientForImage(url).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                // Capped while reading, not after: the URL comes from an emoji pack, and a
                // host that serves an endless body would otherwise fill the heap first.
                val body = response.body
                if (body.contentLength() > MAX_EMOJI_BYTES) return@withContext null
                val source = body.source()
                if (source.request(MAX_EMOJI_BYTES + 1L)) return@withContext null
                val bytes = source.buffer.readByteArray()
                if (bytes.isEmpty()) return@withContext null
                MarmotEmojiMediaPort.Image(bytes, response.header("Content-Type"))
            }
        }

    override suspend fun uploadCiphertext(ciphertext: ByteArray): String? =
        withContext(Dispatchers.IO) {
            val server =
                account.settings.defaultFileServer.baseUrl
                    .ifBlank { return@withContext null }
            val hash = sha256(ciphertext).toHexKey()
            val auth = account.createBlossomUploadAuth(hash, ciphertext.size.toLong(), "Uploading $hash").toAuthorizationHeader()
            BlossomClient(httpClients.okHttpClientForUploads(server))
                .upload(bytes = ciphertext, contentType = OPAQUE, serverBaseUrl = server, authHeader = auth)
                .url
        }

    companion object {
        private const val OPAQUE = "application/octet-stream"

        /** Custom emoji are icons; anything bigger than this is not worth re-uploading per message. */
        private const val MAX_EMOJI_BYTES = 2 * 1024 * 1024
    }
}
