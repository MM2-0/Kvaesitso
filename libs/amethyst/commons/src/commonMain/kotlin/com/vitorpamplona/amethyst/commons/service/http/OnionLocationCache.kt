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

import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap

/**
 * Maps clearnet hostnames to their advertised .onion equivalents, populated
 * passively by [OnionLocationInterceptor] from any HTTP/WebSocket response
 * that carries an `Onion-Location` header.
 *
 * The [OnionUrlRewriteInterceptor] consults this cache on every outbound
 * request when Tor is active, transparently redirecting to the .onion address
 * so Tor-routed connections avoid exit nodes entirely.
 *
 * Entries expire after [TTL_MS] so that a server that rotates or decommissions
 * its `.onion` address does not permanently break Tor connectivity. After expiry
 * the next request goes out via Tor exit nodes (or clearnet on the no-proxy
 * client); if the server still advertises `Onion-Location` the mapping is
 * refreshed automatically.
 */
class OnionLocationCache {
    private data class Entry(
        val onionUrl: String,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentMap<String, Entry>()

    fun put(
        host: String,
        onionUrl: String,
    ) {
        cache[host] = Entry(onionUrl, TimeUtils.nowMillis() + TTL_MS)
    }

    fun get(host: String): String? {
        val entry = cache[host] ?: return null
        if (TimeUtils.nowMillis() > entry.expiresAtMs) {
            cache.remove(host)
            return null
        }
        return entry.onionUrl
    }

    companion object {
        const val TTL_MS: Long = 24L * 60L * 60L * 1000L
    }
}
