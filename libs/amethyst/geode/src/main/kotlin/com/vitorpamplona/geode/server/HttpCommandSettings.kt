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
package com.vitorpamplona.geode.server

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nipFERelayOverHttp.HttpRelayHandler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * NIP-FE (relay commands over HTTP) as [com.vitorpamplona.geode.KtorRelay] serves it: a `POST` to
 * the relay's URL carrying one REQ, COUNT or EVENT frame. The engine's policies and limits apply as
 * they do on the websocket; these bound what the websocket's per-connection limits cannot, since
 * every request is its own connection.
 */
data class HttpCommandSettings(
    /** Requests running at once across all clients before the rest get 503; 0 is no limit. */
    val maxConcurrent: Int = 256,
    /** Requests one client address may run at once before its next gets 429; 0 is no limit. */
    val maxPerClient: Int = 16,
    /** How long the body may take to arrive; past it, 408. It holds an admission slot meanwhile. */
    val bodyTimeout: Duration = 10.seconds,
    /** How long one answer may run, first byte to last. */
    val deadline: Duration = HttpRelayHandler.DEFAULT_DEADLINE,
    /** The largest body read; the engine's own message limit, when it has one and it is smaller, wins. */
    val maxBodyBytes: Int = 512 * 1024,
    /** Gzip streamed answers for clients that accept it, sync-flushed so lines still arrive as found. */
    val compress: Boolean = true,
    /** The `Retry-After` sent with a 429 or 503 the relay decides itself. */
    val retryAfterSeconds: Int = 1,
    /** Other URLs this relay answers at (its .onion); a NIP-98 `u` may name any of them. */
    val alternateUrls: List<NormalizedRelayUrl> = emptyList(),
    /**
     * Peers whose [clientAddressHeader] is believed: the reverse proxies in front of the relay. A
     * request from anyone else is counted under its own address, whatever the header says.
     */
    val trustedProxies: Set<String> = emptySet(),
    /** Where a trusted proxy writes the client's address; its last entry is the one the proxy saw. */
    val clientAddressHeader: String = "X-Forwarded-For",
)
