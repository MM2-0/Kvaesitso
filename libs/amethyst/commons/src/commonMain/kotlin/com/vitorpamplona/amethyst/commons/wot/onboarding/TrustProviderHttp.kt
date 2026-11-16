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

/**
 * The HTTP a trust provider's sign-up needs: JSON GETs and POSTs with headers. Each platform
 * supplies one (OkHttp on Android and the JVM), picking the per-URL client itself (Tor,
 * proxies). Throws on a transport failure; an HTTP error status is a [TrustProviderHttpResponse].
 */
interface TrustProviderHttp {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): TrustProviderHttpResponse

    suspend fun post(
        url: String,
        jsonBody: String?,
        headers: Map<String, String> = emptyMap(),
    ): TrustProviderHttpResponse

    /** For hosts (previews, tests) that never reach the network. */
    object Unsupported : TrustProviderHttp {
        override suspend fun get(
            url: String,
            headers: Map<String, String>,
        ): TrustProviderHttpResponse = throw UnsupportedOperationException("No HTTP on this platform")

        override suspend fun post(
            url: String,
            jsonBody: String?,
            headers: Map<String, String>,
        ): TrustProviderHttpResponse = throw UnsupportedOperationException("No HTTP on this platform")
    }
}

class TrustProviderHttpResponse(
    val status: Int,
    val body: String,
) {
    val isSuccessful: Boolean get() = status in 200..299
}
