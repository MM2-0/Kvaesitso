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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class EncryptedBlobInterceptor(
    val cache: EncryptionKeyCache,
) : Interceptor {
    private fun Response.decryptOrNullWithErrorCorrection(info: DecryptInformation): Response? {
        // The whole blob has to be in memory to decrypt it (the AEAD tag covers
        // all of it), but read the body once and close it: peekBody(MAX) used to
        // buffer a second full copy and left the original stream — and its
        // connection-pool slot, since this is a network interceptor — open.
        val body = this.body
        val contentType = body.contentType()
        val bytes = body.use { it.bytes() }

        // Tries the correct way first
        // if it fails, tries to decrypt as UTF8 nonce, which was how
        // 0xChat started encrypting
        val decrypted =
            info.cipher.decryptOrNull(bytes) ?: if (info.cipher is AESGCM) {
                info.cipher.copyUsingUTF8Nonce().decryptOrNull(bytes)
            } else {
                null
            }

        if (decrypted == null) {
            return null
        }

        return newBuilder()
            .apply {
                body(
                    decrypted.toResponseBody(
                        info.mimeType?.toMediaTypeOrNull() ?: contentType,
                    ),
                )
                // removes hints that would make the app requrest partial byte arrays
                // in videos, which are impossible to decrypt.
                removeHeader("accept-ranges")
                // Fixes the size of the body array
                header("content-length", decrypted.size.toString())
                // Trusts the mimetype from the event is better than the mimetype from the server
                info.mimeType?.let { header("content-type", it) }
            }.build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val encryptionKeys = cache.get(request.url.toString())

        // We cannot use Range requests (partial byte arrays)
        // in encrypted payloads because we won't be able to
        // decrypt partial byte arrays.
        val newRequest =
            if (encryptionKeys != null) {
                request
                    .newBuilder()
                    .removeHeader("Range")
                    .build()
            } else {
                request
            }

        val response = chain.proceed(newRequest)

        if (encryptionKeys == null) {
            return response
        }

        if (response.isSuccessful) {
            return response.decryptOrNullWithErrorCorrection(encryptionKeys) ?: response
        } else {
            // Log redirections to be able to use the cipher.
            response.header("Location")?.let {
                cache.add(it, encryptionKeys)
            }
        }
        return response
    }
}

/** 0xChat's early encrypted blobs used the hex of the nonce, as UTF-8 bytes, for the nonce. */
private fun AESGCM.copyUsingUTF8Nonce(): AESGCM = AESGCM(keyBytes, nonce.toHexKey().toByteArray(Charsets.UTF_8))
