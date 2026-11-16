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

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respondText

/**
 * NIP-11 GET handler. Branches on the `Accept` header so a single path
 * can serve both flavors of GET we expect:
 *
 *  - `Accept: application/nostr+json` → respond with the live relay
 *    info JSON via [liveJson]. CORS-open so browser-based clients
 *    (which can't set custom headers cross-origin without preflight)
 *    can still load it.
 *  - anything else → respond 426 Upgrade Required with a short hint
 *    pointing at the two valid ways to talk to this endpoint
 *    (WebSocket upgrade or NIP-11 Accept).
 *
 * [liveJson] is a callback rather than a snapshot string because the
 * NIP-86 `changerelay*` admin RPCs mutate the in-memory doc while the
 * relay is running; we must read the latest JSON on every request.
 */
internal class Nip11HttpRoute(
    private val liveJson: () -> String,
) {
    suspend fun handle(call: ApplicationCall) {
        val accept = call.request.header(HttpHeaders.Accept).orEmpty()
        if (accept.contains(NIP11_CONTENT_TYPE)) {
            call.response.headers.append("Access-Control-Allow-Origin", "*")
            call.respondText(liveJson(), ContentType.parse(NIP11_CONTENT_TYPE))
        } else {
            call.respondText(
                "Use a Nostr client (NIP-01 WebSocket) or send Accept: $NIP11_CONTENT_TYPE (NIP-11).",
                ContentType.Text.Plain,
                HttpStatusCode.UpgradeRequired,
            )
        }
    }

    companion object {
        const val NIP11_CONTENT_TYPE = "application/nostr+json"
    }
}
