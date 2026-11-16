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
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * A one-shot, lightweight reachability check for a Blossom server. Issues a `HEAD` to
 * the server's `/upload` endpoint (BUD-01/BUD-02) and classifies the outcome by
 * round-trip time. Any HTTP response — even 401/404/405 — counts as reachable; only
 * connection-level failures map to [ServerHealth.Offline].
 *
 * The `/upload` path is probed rather than the bare root because CDN-fronted hosts
 * (e.g. cdn.satellite.earth) don't answer `/` at all and would time out, wrongly
 * reading as offline even though uploads work. `/upload` is the endpoint that
 * actually matters for a media upload target.
 *
 * Mirrors the timeout/short-circuit shape of
 * [com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.LocalBlossomCacheProbe],
 * but runs per-server and returns latency-classified status rather than a boolean.
 */
object MediaServerHealthProbe {
    /** Round-trip time above which a reachable server is reported as [ServerHealth.Slow]. */
    const val SLOW_THRESHOLD_MS: Long = 1_000L
    private const val PROBE_TIMEOUT_MS: Long = 5_000L

    /**
     * How long a probe result is reused before the server is re-checked. The cache is
     * process-wide (this is a singleton) so results survive the screen's ViewModel being
     * recreated on each open, mirroring [com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.LocalBlossomCacheProbe].
     */
    private const val CACHE_TTL_MS: Long = 60_000L

    private class CachedResult(
        val status: ServerHealth,
        val atMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CachedResult>()

    /** The cached status for [baseUrl] if still within [CACHE_TTL_MS], else null. */
    fun cached(baseUrl: String): ServerHealth? {
        val entry = cache[baseUrl] ?: return null
        return if (TimeUtils.nowMillis() - entry.atMs < CACHE_TTL_MS) entry.status else null
    }

    suspend fun probe(
        baseUrl: String,
        clientForUrl: (String) -> OkHttpClient,
    ): ServerHealth {
        cached(baseUrl)?.let { return it }
        val result = runProbe(baseUrl, clientForUrl)
        cache[baseUrl] = CachedResult(result, TimeUtils.nowMillis())
        return result
    }

    private suspend fun runProbe(
        baseUrl: String,
        clientForUrl: (String) -> OkHttpClient,
    ): ServerHealth =
        try {
            val client =
                clientForUrl(baseUrl)
                    .newBuilder()
                    .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build()

            val request =
                Request
                    .Builder()
                    .url(BlossomServerUrl.upload(baseUrl))
                    .head()
                    .build()

            val startedAt = TimeUtils.nowMillis()
            client.newCall(request).executeAsync().use {
                // The status code doesn't matter — /upload commonly answers 401/404/405
                // without auth. Getting any response back proves the host is reachable.
                val elapsed = TimeUtils.nowMillis() - startedAt
                if (elapsed > SLOW_THRESHOLD_MS) ServerHealth.Slow else ServerHealth.Online
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ServerHealth.Offline
        }
}

actual fun cachedMediaServerHealth(baseUrl: String): ServerHealth? = MediaServerHealthProbe.cached(baseUrl)

actual suspend fun IRoleBasedHttpClientBuilder.probeMediaServer(baseUrl: String): ServerHealth = MediaServerHealthProbe.probe(baseUrl, ::okHttpClientForPreview)
