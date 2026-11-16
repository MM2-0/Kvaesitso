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

import com.vitorpamplona.quartz.nip86RelayManagement.server.Nip86HttpHandler
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respondText

/**
 * Ktor adapter for the canonical NIP-86 HTTP flow encapsulated by
 * [Nip86HttpHandler]. Intentionally thin: pull the Authorization
 * header and body bytes out of the [ApplicationCall], hand them to
 * the handler, map each [Nip86HttpHandler.Response] variant to a
 * Ktor status/header/body.
 *
 * Always wired — when the relay's admin allow-list is empty, every
 * request just fails the [Nip86HttpHandler.Response.NotAdmin] check
 * and returns 403. No "is admin on?" branch in either the handler
 * or the route.
 *
 * Bounded body read happens here (Ktor exposes `ByteReadChannel`,
 * which is framework-specific) — we stop reading as soon as we'd
 * exceed [Nip86HttpHandler.maxBodyBytes] so an unauthenticated
 * attacker can't OOM the relay with a giant stream.
 *
 * Audit logging stays here, off [Nip86HttpHandler.Response.Ok] — the
 * handler keeps logging policy out of quartz; the geode adapter picks
 * a stderr line format and runs with it.
 */
internal class Nip86HttpRoute(
    private val handler: Nip86HttpHandler,
) {
    suspend fun handle(call: ApplicationCall) {
        val body =
            readBoundedBody(call, handler.maxBodyBytes) ?: return call.respondText(
                "request body exceeds ${handler.maxBodyBytes}-byte cap",
                ContentType.Text.Plain,
                HttpStatusCode.PayloadTooLarge,
            )
        val authHeader = call.request.header(HttpHeaders.Authorization)

        when (val r = handler.handle(authHeader, body)) {
            is Nip86HttpHandler.Response.PayloadTooLarge -> {
                call.respondText(
                    "request body exceeds ${r.cap}-byte cap",
                    ContentType.Text.Plain,
                    HttpStatusCode.PayloadTooLarge,
                )
            }

            Nip86HttpHandler.Response.MissingAuth -> {
                call.response.headers.append(HttpHeaders.WWWAuthenticate, Nip86HttpHandler.WWW_AUTHENTICATE)
                call.respondText(
                    "missing Authorization header (NIP-98)",
                    ContentType.Text.Plain,
                    HttpStatusCode.Unauthorized,
                )
            }

            is Nip86HttpHandler.Response.BadAuth -> {
                call.respondText(
                    "invalid NIP-98 Authorization: ${r.reason}",
                    ContentType.Text.Plain,
                    HttpStatusCode.Unauthorized,
                )
            }

            Nip86HttpHandler.Response.NotAdmin -> {
                call.respondText(
                    "pubkey is not on the admin list",
                    ContentType.Text.Plain,
                    HttpStatusCode.Forbidden,
                )
            }

            is Nip86HttpHandler.Response.BadRequest -> {
                call.respondText(
                    r.reason,
                    ContentType.Text.Plain,
                    HttpStatusCode.BadRequest,
                )
            }

            is Nip86HttpHandler.Response.Ok -> {
                audit(r)
                call.respondText(
                    r.json,
                    ContentType.parse(Nip86HttpHandler.CONTENT_TYPE),
                    HttpStatusCode.OK,
                )
            }
        }
    }

    /**
     * Single-line stderr audit. Operators grep on "nip86" / pubkey /
     * method without needing a logging framework. Best-effort — a
     * failed log line must not fail the response.
     */
    private fun audit(ok: Nip86HttpHandler.Response.Ok) {
        runCatching {
            System.err.println(
                "nip86 audit pubkey=${ok.pubkey} method=${ok.request.method} ok=${ok.response.error == null}" +
                    (ok.response.error?.let { " error=$it" } ?: ""),
            )
        }
    }
}
