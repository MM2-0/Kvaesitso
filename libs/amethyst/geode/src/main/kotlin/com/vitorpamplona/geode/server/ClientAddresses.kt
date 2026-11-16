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

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/**
 * Who a request or websocket counts against: the peer's address, or, when the peer is one of
 * [trustedProxies], the last address in its [header] — the one that proxy saw. Earlier entries
 * are whatever the client claimed and are never believed. Shared by NIP-FE admission and the
 * per-client rate limits, so both count the same client the same way.
 */
class ClientAddresses(
    private val trustedProxies: Set<String> = emptySet(),
    private val header: String = "X-Forwarded-For",
) {
    fun of(call: ApplicationCall): String {
        val peer = call.request.local.remoteAddress
        if (peer !in trustedProxies) return peer
        return call.request
            .header(header)
            ?.substringAfterLast(',')
            ?.trim()
            ?.ifEmpty { null } ?: peer
    }
}
