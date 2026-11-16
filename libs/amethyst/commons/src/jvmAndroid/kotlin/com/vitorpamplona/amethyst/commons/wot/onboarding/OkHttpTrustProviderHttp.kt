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
package com.vitorpamplona.amethyst.commons.wot.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import java.io.IOException

/** [TrustProviderHttp] over OkHttp. [okHttpClient] picks the client for each URL (Tor, proxies). */
class OkHttpTrustProviderHttp(
    private val okHttpClient: (String) -> OkHttpClient,
) : TrustProviderHttp {
    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): TrustProviderHttpResponse = execute(url, Request.Builder().url(url).get(), headers)

    override suspend fun post(
        url: String,
        jsonBody: String?,
        headers: Map<String, String>,
    ): TrustProviderHttpResponse = execute(url, Request.Builder().url(url).post((jsonBody ?: "").toRequestBody(JSON)), headers)

    private suspend fun execute(
        url: String,
        builder: Request.Builder,
        headers: Map<String, String>,
    ): TrustProviderHttpResponse {
        headers.forEach { (name, value) -> builder.header(name, value) }
        builder.header("Accept", "application/json")
        return okHttpClient(url).newCall(builder.build()).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                // Setup answers are a few KB; a misbehaving endpoint must not stream the app out of memory.
                val source = response.body.source()
                if (!source.request(MAX_BODY_BYTES + 1)) {
                    TrustProviderHttpResponse(response.code, source.buffer.readUtf8())
                } else {
                    throw IOException("$url answered with more than $MAX_BODY_BYTES bytes")
                }
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_BODY_BYTES = 1_048_576L
    }
}
