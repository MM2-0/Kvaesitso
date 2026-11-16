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
package com.vitorpamplona.amethyst.commons.mediaServers

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder

/**
 * Reachability status of a media server, shown as a colored dot next to each
 * entry in the Media Servers list.
 */
enum class ServerHealth {
    /** Not probed yet. */
    Unknown,

    /** A probe is in flight. */
    Checking,

    /** Responded quickly. */
    Online,

    /** Responded, but slower than about a second. */
    Slow,

    /** Could not be reached (DNS, refused, timeout, TLS). */
    Offline,
}

/** The status from a probe of [baseUrl] that is still fresh, or null when it needs re-checking. */
expect fun cachedMediaServerHealth(baseUrl: String): ServerHealth?

/**
 * Checks whether the Blossom server at [baseUrl] answers, over this account's preview client (so
 * Tor settings apply), and classifies it by round-trip time. Results are cached briefly.
 */
expect suspend fun IRoleBasedHttpClientBuilder.probeMediaServer(baseUrl: String): ServerHealth
