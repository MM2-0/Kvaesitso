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
package com.vitorpamplona.amethyst.commons.service.lnurl

/**
 * The HTTP an LNURL-pay flow needs: a GET that returns the status, the reason phrase and the body
 * as text. Each platform supplies one (OkHttp on Android and the JVM). Implementations pick the
 * per-URL client themselves (Tor, proxies), run off the main thread, and throw on a transport
 * failure; an HTTP error status is an [LnurlHttpResponse], not an exception.
 */
fun interface LnurlHttpTransport {
    suspend fun get(url: String): LnurlHttpResponse
}

/** One LNURL server response. [reason] is the HTTP reason phrase, often empty over HTTP/2. */
class LnurlHttpResponse(
    val status: Int,
    val reason: String,
    val body: String,
) {
    val isSuccessful: Boolean get() = status in 200..299
}
