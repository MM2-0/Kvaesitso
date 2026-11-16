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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Hex
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Retries a Blossom blob download with a BUD-01 `t=get` authorization when the
 * server answers `401`.
 *
 * Most Blossom / NIP-96 hosts serve blobs anonymously, so images load without
 * any signing overhead. A few gate reads behind auth — Buzz's private media
 * relay (`*.communities.buzz.xyz`) returns
 * `401 {"error":"authentication failed"}` to an anonymous `GET`. For those we
 * sign a kind-24242 read-auth event (via [authHeaderProvider]) and replay the
 * request once with `Authorization: Nostr <base64-event>`.
 *
 * Gating is deliberately narrow so we never turn an unrelated `401` into a
 * second request storm:
 *  - only `GET` requests,
 *  - only when the request doesn't already carry an `Authorization` header,
 *  - only when the URL's last path segment is a Blossom sha256 filename
 *    (`<64-hex>` optionally followed by an extension such as `.png` or
 *    `.thumb.jpg`),
 *  - only after the anonymous attempt actually returned `401`,
 *  - and at most one retry (an application interceptor's second `chain.proceed`
 *    runs the downstream chain again, it does not re-enter this interceptor).
 *
 * This interceptor never signs and never waits. [cachedHeaderProvider] is a
 * pure cache read and [onAuthRequired] is fire-and-forget: `intercept` runs on
 * an OkHttp dispatcher thread, where blocking would hold one of the 16 per-host
 * slots for the whole signing window and stall every other image from that
 * host. The signed *retry* therefore lives one layer up, in
 * `BlossomReadAuthFetcher`, which is `suspend` and can await the signature
 * without occupying a slot.
 *
 * The first blob from an auth-gated host still costs an extra round trip
 * (anonymous `GET` -> `401` -> signed retry by the fetcher), but the host is
 * then remembered in [knownAuthHosts] so every later blob from it is signed
 * **up front** from the cache — one round trip, not two. This matters on a Buzz
 * community feed where nearly every image comes from the same gated host.
 * Callers that cannot retry (e.g. the media3 video datasource) get the token on
 * their next request, once [onAuthRequired] has landed it in the cache.
 */
class BlossomReadAuthInterceptor(
    /** Pure cache read — must not sign, must not block. */
    private val cachedHeaderProvider: (host: String) -> String?,
    /** Fire-and-forget: starts a signature for a host we just learned is gated. */
    private val onAuthRequired: (host: String) -> Unit,
) : Interceptor {
    // Hosts observed to answer 401 to an anonymous Blossom GET. Small (a user
    // follows a handful of auth-gated servers at most) and shared across all
    // clients derived from the same factory. newKeySet() is thread-safe for the
    // concurrent reads/writes of parallel feed downloads.
    private val knownAuthHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!request.method.equals("GET", ignoreCase = true) ||
            request.header("Authorization") != null
        ) {
            return chain.proceed(request)
        }

        // The hash is a gate, not an input: read-auth applies only to Blossom
        // blob URLs. The token itself is host-scoped and carries no `x` tag.
        blossomHashOrNull(request.url.encodedPath) ?: return chain.proceed(request)
        val host = request.url.host

        // Known-gated host: skip the anonymous probe and sign the first attempt.
        // Falls through to anonymous only when we can't produce a token (no
        // signer / timeout) — the server would 401 either way.
        if (host in knownAuthHosts) {
            cachedHeaderProvider(host)?.let { header ->
                return chain.proceed(request.withAuth(header))
            }
        }

        val response = chain.proceed(request)
        if (response.code != 401) return response

        // Learn the host so its next blob is signed up front.
        knownAuthHosts.add(host)

        // Start the signature but do not wait for it: this thread holds a
        // per-host dispatcher slot. BlossomReadAuthFetcher performs the signed
        // retry for this very request from a coroutine.
        onAuthRequired(host)

        return response
    }

    private fun Request.withAuth(header: String) =
        newBuilder()
            .header("Authorization", header)
            .build()

    companion object {
        /**
         * Extracts the sha256 blob id from a Blossom URL path. The blob is the
         * last path segment, up to its first `.` — so both `<hash>.png` and the
         * derived `<hash>.thumb.jpg` resolve to `<hash>`. Returns `null` when the
         * segment isn't a 64-char hex string.
         *
         * Uses Quartz's unrolled [Hex.isHex64] rather than a regex — this runs on
         * every media URL the feed loads. [Hex.isHex64] only checks the first 64
         * chars and doesn't verify total length, so the `length == 64` guard is
         * what rejects longer segments.
         */
        fun blossomHashOrNull(encodedPath: String): HexKey? {
            val base = encodedPath.substringAfterLast('/').substringBefore('.').lowercase()
            return if (base.length == 64 && Hex.isHex64(base)) base else null
        }
    }
}
