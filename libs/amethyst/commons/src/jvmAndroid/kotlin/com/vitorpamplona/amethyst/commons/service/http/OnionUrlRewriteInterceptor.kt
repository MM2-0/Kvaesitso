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

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Application interceptor that rewrites outbound requests to use a known
 * .onion address when one is available in [OnionLocationCache].
 *
 * Only added to Tor-enabled OkHttpClient instances, so .onion substitution
 * never happens on clearnet connections (where .onion DNS would not resolve).
 *
 * Scheme is derived from the `Onion-Location` header value to keep the request
 * in the correct protocol family:
 * - WebSocket originals (`ws`/`wss`) → `ws` or `wss` based on the onion scheme
 * - HTTP originals (`http`/`https`) → `http` or `https` based on the onion scheme
 *
 * A `.onion` server advertising `http://` for a clearnet `https://` host is
 * intentional and safe: the Tor circuit provides end-to-end encryption
 * equivalent to TLS for onion services, so an `http://` onion connection is
 * not a downgrade in practice.
 *
 * Port and path are preserved from the onion URL and original request
 * respectively. An unparseable cached URL falls through to the original.
 */
class OnionUrlRewriteInterceptor(
    private val cache: OnionLocationCache,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val onionUrlStr = cache.get(original.url.host) ?: return chain.proceed(original)
        val onionUrl = runCatching { onionUrlStr.toHttpUrl() }.getOrNull() ?: return chain.proceed(original)

        val newScheme =
            when (original.url.scheme) {
                "ws", "wss" -> if (onionUrl.scheme == "https") "wss" else "ws"
                "http", "https" -> if (onionUrl.scheme == "https") "https" else "http"
                else -> onionUrl.scheme
            }

        val rewritten =
            original
                .newBuilder()
                .url(
                    original.url
                        .newBuilder()
                        .scheme(newScheme)
                        .host(onionUrl.host)
                        .port(onionUrl.port)
                        .build(),
                ).build()

        return chain.proceed(rewritten)
    }
}
