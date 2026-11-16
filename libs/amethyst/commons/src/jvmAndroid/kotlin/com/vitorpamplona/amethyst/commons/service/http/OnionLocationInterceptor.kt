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

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Application interceptor that passively captures `Onion-Location` headers from
 * every HTTP response — NIP-11 documents, WebSocket upgrade handshakes (101),
 * image/media servers, anything — and records the mapping into
 * [OnionLocationCache].
 *
 * No extra requests are made; discovery is a free by-product of traffic that
 * already happens. When the Tor-enabled client later connects to the same host,
 * [OnionUrlRewriteInterceptor] swaps in the cached .onion address so the
 * connection avoids exit nodes entirely.
 *
 * Registered as an application interceptor so that `chain.request().url.host`
 * always returns the original clearnet hostname — the correct cache key. If it
 * were a network interceptor it would run after [OnionUrlRewriteInterceptor]
 * has already rewritten the host to `.onion`, causing cache entries from
 * onion-routed responses to be stored under the `.onion` hostname instead of
 * the clearnet hostname that [OnionUrlRewriteInterceptor] actually looks up.
 */
class OnionLocationInterceptor(
    private val cache: OnionLocationCache,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val onionLocation = response.header("Onion-Location")
        if (onionLocation != null && isOnionService(onionLocation)) {
            cache.put(chain.request().url.host, onionLocation)
        }
        return response
    }

    private companion object {
        /**
         * Only a `.onion` target may be cached. [OnionUrlRewriteInterceptor]
         * re-points every Tor request for the host at the cached location and
         * deliberately allows https → http because the Tor circuit provides the
         * transport security — a guarantee that only holds for onion services.
         * A clearnet (or attacker-supplied) `Onion-Location` would otherwise
         * redirect LNURL/NIP-05/media traffic to an arbitrary plaintext host
         * for the cache TTL.
         */
        fun isOnionService(location: String): Boolean {
            val host = location.toHttpUrlOrNull()?.host ?: return false
            return host.endsWith(".onion", ignoreCase = true)
        }
    }
}
