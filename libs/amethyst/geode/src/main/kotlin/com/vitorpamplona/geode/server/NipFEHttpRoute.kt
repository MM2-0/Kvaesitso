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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RateLimitPolicy
import com.vitorpamplona.quartz.nip86RelayManagement.server.Nip86HttpHandler
import com.vitorpamplona.quartz.nipFERelayOverHttp.HttpRelayHandler
import com.vitorpamplona.quartz.nipFERelayOverHttp.HttpRelayLines
import com.vitorpamplona.quartz.nipFERelayOverHttp.HttpRelayRequest
import com.vitorpamplona.quartz.nipFERelayOverHttp.HttpRelayResponse
import com.vitorpamplona.quartz.nipFERelayOverHttp.HttpRelayStatus
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * NIP-FE over Ktor: the host half of [HttpRelayHandler], on POSTs to the relay's URL that are not
 * NIP-86 calls (see [isCommand]). It admits the request, reads the body up to
 * the cap, and writes the handler's answer as `application/x-ndjson` — one refusal line with its
 * status, or a 200 streamed and flushed frame by frame — with the headers the status calls for
 * (`WWW-Authenticate` on 401, `Retry-After` on 429 and 503) and the CORS and no-buffering headers
 * every answer carries.
 */
internal class NipFEHttpRoute(
    private val handler: HttpRelayHandler,
    private val settings: HttpCommandSettings,
    private val clients: ClientAddresses = ClientAddresses(settings.trustedProxies, settings.clientAddressHeader),
) {
    private val admission = HttpAdmission(settings.maxConcurrent, settings.maxPerClient)

    private val bodyCap: Int = minOf(settings.maxBodyBytes.toLong(), handler.maxBodyBytes ?: Long.MAX_VALUE).toInt()

    /** Requests being answered right now. */
    val inFlight: Int get() = admission.inFlight

    /** A CORS preflight: any origin may POST with a NIP-98 `Authorization`; no cookies are involved. */
    suspend fun preflight(call: ApplicationCall) {
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.response.header(HttpHeaders.AccessControlAllowMethods, "POST, OPTIONS")
        call.response.header(HttpHeaders.AccessControlAllowHeaders, "Authorization, Content-Type")
        call.response.header(HttpHeaders.AccessControlMaxAge, PREFLIGHT_MAX_AGE_SECONDS.toString())
        call.respond(HttpStatusCode.NoContent)
    }

    /**
     * Whether a POST to the relay's URL is a NIP-FE command: anything but NIP-86's
     * `application/nostr+json+rpc`, since commands need no `Content-Type` at all.
     */
    fun isCommand(call: ApplicationCall): Boolean {
        // Compared as text: parsing it would throw on a malformed header, and a command needs none.
        val type = call.request.header(HttpHeaders.ContentType) ?: return true
        return !type.substringBefore(';').trim().equals(Nip86HttpHandler.CONTENT_TYPE, ignoreCase = true)
    }

    /** Answers the command in the body. Admission runs first, so a refused request spends no NIP-98 token. */
    suspend fun handle(call: ApplicationCall) {
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.response.header(HttpHeaders.AccessControlExposeHeaders, "${HttpHeaders.WWWAuthenticate}, ${HttpHeaders.RetryAfter}")
        call.response.header(HttpHeaders.CacheControl, "no-store")
        // nginx and friends buffer responses by default, which holds back the lines streaming delivers.
        call.response.header(ACCEL_BUFFERING, "no")

        val client = clients.of(call)
        // Counted like the rate limits count: an IPv6 client by its /64, so rotating through the
        // addresses one host holds does not buy more concurrent requests.
        val verdict =
            admission.admit(RateLimitPolicy.clientKeyOf(client)) {
                // Bounded in time too: a body trickled in forever would hold this admission slot forever.
                val body =
                    try {
                        withTimeout(settings.bodyTimeout) { readBoundedBody(call, bodyCap) }
                    } catch (_: TimeoutCancellationException) {
                        call.response.header(HttpHeaders.Connection, "close")
                        return@admit respondLine(
                            call,
                            REQUEST_TIMEOUT,
                            HttpRelayHandler.notice(MachineReadablePrefix.INVALID.format("the body did not arrive within ${settings.bodyTimeout}")),
                        )
                    } ?: return@admit respondLine(
                        call,
                        HttpRelayStatus.PAYLOAD_TOO_LARGE,
                        HttpRelayHandler.notice(MachineReadablePrefix.INVALID.format("the command exceeds $bodyCap bytes")),
                    )
                handler.handle(HttpRelayRequest(call.request.header(HttpHeaders.Authorization), body, client), Answer(call))
            }
        when (verdict) {
            HttpAdmission.Verdict.ADMITTED -> {}

            HttpAdmission.Verdict.CLIENT_BUSY -> {
                respondLine(
                    call,
                    HttpRelayStatus.TOO_MANY_REQUESTS,
                    HttpRelayHandler.notice(MachineReadablePrefix.RATE_LIMITED.format("over ${settings.maxPerClient} requests at once from this client")),
                )
            }

            HttpAdmission.Verdict.RELAY_BUSY -> {
                respondLine(
                    call,
                    HttpRelayStatus.UNAVAILABLE,
                    HttpRelayHandler.notice(MachineReadablePrefix.RATE_LIMITED.format("the relay is at capacity")),
                )
            }
        }
    }

    /**
     * Whether `Accept-Encoding` takes gzip: listed by name or as `*`, without `q=0`. A one-line
     * answer is never compressed; only a streamed one is worth it.
     */
    private fun acceptsGzip(call: ApplicationCall): Boolean =
        call.request
            .header(HttpHeaders.AcceptEncoding)
            .orEmpty()
            .split(',')
            .any { entry ->
                val parts = entry.split(';').map { it.trim() }
                val coding = parts.first().lowercase()
                val q =
                    parts
                        .drop(1)
                        .firstOrNull { it.startsWith("q=") }
                        ?.removePrefix("q=")
                        ?.toDoubleOrNull() ?: 1.0
                (coding == "gzip" || coding == "*") && q > 0.0
            }

    /** A one-line answer: a refusal, or a command answered at once. */
    private suspend fun respondLine(
        call: ApplicationCall,
        status: Int,
        frame: String,
        retryAfterMs: Long? = null,
    ) {
        when (status) {
            HttpRelayStatus.UNAUTHORIZED -> {
                call.response.header(HttpHeaders.WWWAuthenticate, WWW_AUTHENTICATE)
            }

            // A refusal that states its NIP-01 back-off sends that, rounded up to whole seconds
            // (the header's unit); the relay's own admission refusals send the configured one.
            HttpRelayStatus.TOO_MANY_REQUESTS, HttpRelayStatus.UNAVAILABLE -> {
                val seconds = retryAfterMs?.let { (it + 999) / 1000 } ?: settings.retryAfterSeconds.toLong()
                call.response.header(HttpHeaders.RetryAfter, seconds.toString())
            }
        }
        call.respondText(frame + "\n", NDJSON, HttpStatusCode.fromValue(status))
    }

    private inner class Answer(
        private val call: ApplicationCall,
    ) : HttpRelayResponse {
        override suspend fun single(
            status: Int,
            frame: String,
        ) = respondLine(call, status, frame)

        override suspend fun single(
            status: Int,
            frame: String,
            retryAfterMs: Long?,
        ) = respondLine(call, status, frame, retryAfterMs)

        /**
         * Chunked: each flush puts what the handler wrote on the wire, and a full socket suspends the
         * writer. Gzipped when the client takes it and the relay allows it, sync-flushed at every
         * flush so the lines still arrive as they are found.
         */
        override suspend fun stream(lines: suspend HttpRelayLines.() -> Unit) {
            val gzip = settings.compress && acceptsGzip(call)
            call.response.header(HttpHeaders.Vary, HttpHeaders.AcceptEncoding)
            if (gzip) call.response.header(HttpHeaders.ContentEncoding, "gzip")
            call.respondBytesWriter(NDJSON, HttpStatusCode.OK) {
                val out = this
                if (gzip) {
                    val body = GzipLines(out)
                    try {
                        object : HttpRelayLines {
                            override suspend fun line(frame: String) = body.line(frame)

                            override suspend fun flush() = body.flush()
                        }.lines()
                        body.finish()
                    } finally {
                        body.close()
                    }
                } else {
                    object : HttpRelayLines {
                        override suspend fun line(frame: String) {
                            out.writeStringUtf8(frame)
                            out.writeStringUtf8("\n")
                        }

                        override suspend fun flush() = out.flush()
                    }.lines()
                }
            }
        }
    }

    companion object {
        val NDJSON = ContentType("application", "x-ndjson")
        const val REQUEST_TIMEOUT = 408
        const val WWW_AUTHENTICATE = "Nostr"
        const val ACCEL_BUFFERING = "X-Accel-Buffering"
        const val PREFLIGHT_MAX_AGE_SECONDS = 86_400
    }
}
