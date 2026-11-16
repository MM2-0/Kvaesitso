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
package com.vitorpamplona.amethyst

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.service.http.EncryptedBlobInterceptor
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DMFileDecryptionTest {
    // Key cache to download and decrypt encrypted files before caching them.
    val keyCache = EncryptionKeyCache()

    val url = "https://cdn.satellite.earth/812fd4cf9d4d4b59c141ecd6a6c08c7571b5872237ad6477916cb2d119b5cacd"
    val cipher =
        AESGCM(
            "7b184b3849e161027bab1aac9f2d96eb22bfe02c8dc34cad5d2cc436a64f191d".hexToByteArray(),
            "3936923383652e3f0d72bfd40568b435".hexToByteArray(),
        )
    val decryptedSize = 1277122
    val expectedMimeType = "video/mp4"

    @Test
    fun runDownloadAndDecryptVideo() {
        val client =
            OkHttpClient
                .Builder()
                .addNetworkInterceptor(EncryptedBlobInterceptor(keyCache))
                .build()

        keyCache.add(url, cipher, "video/mp4")

        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()

        client.newCall(request).execute().use {
            assertEquals(decryptedSize, it.body.bytes().size)
            assertEquals(expectedMimeType, it.body.contentType().toString())
        }
    }
}
