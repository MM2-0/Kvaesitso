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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

/**
 * OkHttp-backed [NestsClient] used on JVM + Android. A shared [OkHttpClient]
 * can be injected so the app reuses connection pools / interceptors across
 * the process; the default constructor creates a dedicated client.
 *
 * [callTimeoutMs] enforces an upper bound on each `mintToken` round trip,
 * including all transport / 429 retries. The injected [OkHttpClient] may
 * have its own per-call/connect/read timeouts, but those don't bound the
 * retry loop itself — without this watchdog, a stalled server can hold
 * the reconnect orchestrator indefinitely (the orchestrator is suspended
 * inside `connectNestsListener`'s mint step).
 */
class OkHttpNestsClient(
    private val callTimeoutMs: Long = DEFAULT_CALL_TIMEOUT_MS,
    private val httpClient: (String) -> OkHttpClient,
) : NestsClient {
    override suspend fun mintToken(
        room: NestsRoomConfig,
        publish: Boolean,
        signer: NostrSigner,
    ): String {
        val url = nestsAuthUrl(room.authBaseUrl)
        val bodyJson =
            buildString {
                append('{')
                append("\"namespace\":\"").append(room.moqNamespace()).append('"')
                append(",\"publish\":").append(publish)
                append('}')
            }
        val bodyBytes = bodyJson.encodeToByteArray()

        // NIP-98 events embed `created_at`; the moq-auth reference
        // accepts a 60 s validity window. A retry that waits longer
        // than that (e.g. exponential backoff after 429) MUST re-sign
        // or the server returns 401 "Event too old". So we build the
        // request lazily on every attempt instead of once up front.
        val buildRequest: suspend () -> Request = {
            val authHeader =
                NestsAuth.header(
                    signer = signer,
                    url = url,
                    method = "POST",
                    payload = bodyBytes,
                )
            Request
                .Builder()
                .url(url)
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .build()
        }

        return withContext(Dispatchers.IO) {
            // Hard upper bound on the entire mint round-trip (including
            // retries) so a stalled server can't suspend the reconnect
            // orchestrator indefinitely. The injected OkHttpClient's
            // own callTimeout doesn't cover the retry loop.
            val response =
                try {
                    withTimeout(callTimeoutMs) { executeWithRetry(buildRequest, url) }
                } catch (e: TimeoutCancellationException) {
                    throw NestsException("nests mint timed out after ${callTimeoutMs}ms for $url", e)
                }
            response
                .use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw NestsException(
                            "nests server returned ${response.code} for $url: $body",
                            status = response.code,
                        )
                    }
                    try {
                        NestsTokenResponse.parse(body).token
                    } catch (e: IOException) {
                        throw NestsException("Malformed nests response from $url", e)
                    } catch (e: IllegalArgumentException) {
                        throw NestsException("Malformed nests response from $url", e)
                    } catch (e: IllegalStateException) {
                        // kotlinx.serialization can throw IllegalStateException
                        // on some malformed input shapes (e.g. unfinished
                        // escapes) instead of SerializationException.
                        throw NestsException("Malformed nests response from $url", e)
                    } catch (e: kotlinx.serialization.SerializationException) {
                        throw NestsException("Malformed nests response from $url", e)
                    }
                }
        }
    }

    /**
     * Send [request] and tolerate two recoverable failure modes:
     *
     *   1. Transport hiccup. OkHttp's built-in `retryOnConnectionFailure`
     *      does NOT retry POSTs once any byte of the request body has
     *      been written — but a stale pooled connection can RST or EOF
     *      *exactly* in that window, especially on mobile networks (and
     *      during interop test runs after an idle gap between test
     *      classes). One retry on `SocketException` / `EOFException` /
     *      `IOException` recovers cleanly because `Request` builders
     *      are immutable; OkHttp opens a fresh connection on the
     *      second try.
     *
     *   2. HTTP 429 (Too Many Requests). The nostrnests reference
     *      `moq-auth` sidecar rate-limits 20/min/IP; production
     *      back-ends may be stricter. We respect a `Retry-After`
     *      header (delta-seconds OR HTTP-date) when present and fall
     *      back to capped exponential backoff when absent, retrying
     *      up to [MAX_RATE_LIMIT_RETRIES] times. Cancellable: the
     *      backoff suspends with `delay`, so a coroutine cancellation
     *      tears the retry loop down at the next sleep boundary.
     *
     * Anything that's not a transient transport failure or a 429 (HTTP
     * 4xx other than 429, 5xx, malformed response) is left to the
     * caller as before.
     */
    private suspend fun executeWithRetry(
        buildRequest: suspend () -> Request,
        url: String,
    ): Response {
        var transportError: Throwable? = null
        var transportAttempts = 0
        var rateLimitAttempts = 0
        while (true) {
            val request = buildRequest()
            val response: Response =
                try {
                    httpClient(url).newCall(request).executeAsync()
                } catch (e: SocketException) {
                    transportError = e
                    if (++transportAttempts >= MAX_TRANSPORT_RETRIES) throw NestsException("Failed to reach $url", e)
                    continue
                } catch (e: EOFException) {
                    transportError = e
                    if (++transportAttempts >= MAX_TRANSPORT_RETRIES) throw NestsException("Failed to reach $url", e)
                    continue
                } catch (e: IOException) {
                    // OkHttp wraps a wide variety of transport faults
                    // (StreamResetException, ConnectionShutdownException,
                    // …) under IOException. Retry once; second pass
                    // either succeeds against a fresh connection or
                    // surfaces the real error.
                    transportError = e
                    if (++transportAttempts >= MAX_TRANSPORT_RETRIES) throw NestsException("Failed to reach $url", e)
                    continue
                }

            if (response.code != 429 || rateLimitAttempts >= MAX_RATE_LIMIT_RETRIES) {
                return response
            }

            val retryAfter = response.header("Retry-After")
            // Drain + close so the connection returns to the pool;
            // the Response we hand back to the caller is the one from
            // the next iteration.
            response.close()
            val delayMs = computeRateLimitBackoffMs(retryAfter, rateLimitAttempts)
            rateLimitAttempts++
            delay(delayMs)
        }
        // Unreachable — every path either returns or throws.
        @Suppress("UNREACHABLE_CODE")
        throw NestsException("Failed to reach $url", transportError)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private const val MAX_TRANSPORT_RETRIES = 2

        /**
         * Default upper bound on a full `mintToken` call, including all
         * transport / 429 retries. Worst-case 429 backoff totals ~63 s
         * per [MAX_RATE_LIMIT_RETRIES] kdoc; 90 s leaves headroom for
         * one slow-responding 200 on top of that.
         */
        const val DEFAULT_CALL_TIMEOUT_MS: Long = 90_000L
    }
}

// Worst-case total wait at INITIAL_BACKOFF_MS=1s, MAX_BACKOFF_MS=16s,
// 7 retries: 1+2+4+8+16+16+16 = 63 s — just over the moq-auth
// reference 60 s/IP rate-limit window, so a clustered burst of mints
// (typical in interop test runs) will outlast the bucket reset
// instead of cascading.
internal const val MAX_RATE_LIMIT_RETRIES = 7

internal const val INITIAL_BACKOFF_MS = 1_000L

internal const val MAX_BACKOFF_MS = 16_000L

/**
 * Translate a `Retry-After` header (RFC 7231 §7.1.3 — either
 * delta-seconds or HTTP-date) into millis to sleep. Falls back to
 * capped exponential backoff (1s, 2s, 4s, 8s, …) when the header is
 * absent or unparseable.
 *
 * `nowMs` is parameterised so date-driven cases are testable without
 * `Thread.sleep`-style time travel.
 */
internal fun computeRateLimitBackoffMs(
    retryAfterHeader: String?,
    attempt: Int,
    nowMs: Long = System.currentTimeMillis(),
): Long {
    if (retryAfterHeader != null) {
        retryAfterHeader.trim().toLongOrNull()?.let { seconds ->
            return seconds.coerceAtLeast(0L) * 1_000L
        }
        try {
            val target = ZonedDateTime.parse(retryAfterHeader, DateTimeFormatter.RFC_1123_DATE_TIME)
            val targetMs = target.withZoneSameInstant(ZoneId.of("UTC")).toInstant().toEpochMilli()
            if (targetMs > nowMs) return targetMs - nowMs
        } catch (_: Throwable) {
            // Fall through to exponential backoff.
        }
    }
    val base = INITIAL_BACKOFF_MS shl attempt
    return min(base, MAX_BACKOFF_MS)
}
