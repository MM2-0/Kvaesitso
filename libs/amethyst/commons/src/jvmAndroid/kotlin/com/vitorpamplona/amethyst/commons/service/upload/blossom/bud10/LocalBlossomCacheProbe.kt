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
package com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Discovers a local Blossom cache running on `http://127.0.0.1:24242` per
 * https://github.com/hzrd149/blossom/blob/master/implementations/local-blossom-cache.md
 *
 * Issues a `HEAD /` request and caches the result with separate positive and
 * negative TTLs so the loopback isn't probed on every image load.
 */
class LocalBlossomCacheProbe(
    private val httpClientBuilder: IRoleBasedHttpClientBuilder,
) {
    private val mutex = Mutex()

    @Volatile
    private var cachedAtMs: Long = 0L

    // Bumped by [markUnavailable]; lets a probe that raced it discard its stale positive result.
    private val unavailableMarks = AtomicInteger(0)

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available

    suspend fun isAvailable(): Boolean {
        val now = currentTimeMs()
        val ttl = if (_available.value) POSITIVE_TTL_MS else NEGATIVE_TTL_MS
        if (cachedAtMs != 0L && now - cachedAtMs < ttl) {
            return _available.value
        }

        return mutex.withLock {
            // Re-check inside the lock in case another caller just refreshed.
            val now2 = currentTimeMs()
            val ttl2 = if (_available.value) POSITIVE_TTL_MS else NEGATIVE_TTL_MS
            if (cachedAtMs != 0L && now2 - cachedAtMs < ttl2) {
                return@withLock _available.value
            }

            val startedAt = unavailableMarks.get()
            val newResult = probe()
            // A refused connection reported while this probe was in flight is newer evidence
            // than a HEAD that may have succeeded just before the cache went away.
            if (newResult && startedAt != unavailableMarks.get()) return@withLock false
            _available.value = newResult
            cachedAtMs = currentTimeMs()
            newResult
        }
    }

    /**
     * Forces the next call to [isAvailable] to re-probe regardless of TTL.
     */
    fun invalidate() {
        cachedAtMs = 0L
    }

    /**
     * Records that the cache just refused a connection. Flipping [available] right away turns
     * the bridge off everywhere it is read, instead of letting every sha256 URL keep failing
     * against the dead loopback port until the positive TTL runs out and someone re-probes.
     */
    fun markUnavailable() {
        unavailableMarks.incrementAndGet()
        cachedAtMs = currentTimeMs()
        _available.value = false
    }

    // Confined to Dispatchers.IO because callers reach this through suspend
    // resolvers invoked from Compose `LaunchedEffect`/`produceState`, which run
    // on the main dispatcher: building the OkHttp client and issuing the HEAD
    // must not touch the UI thread.
    private suspend fun probe(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val baseClient = httpClientBuilder.okHttpClientForPreview(LOCAL_CACHE_BASE)
                val client =
                    baseClient
                        .newBuilder()
                        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .build()

                val request =
                    Request
                        .Builder()
                        .url("$LOCAL_CACHE_BASE/")
                        .head()
                        .build()

                client.newCall(request).executeAsync().use { response ->
                    // Spec says HEAD / returns 2xx when available. Some implementations
                    // may answer 405 (method not allowed) while still being a working
                    // Blossom cache, so treat that as available too.
                    response.isSuccessful || response.code == 405
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
        }

    private fun currentTimeMs(): Long = System.currentTimeMillis()

    companion object {
        const val LOCAL_CACHE_BASE: String = "http://127.0.0.1:24242"
        private const val POSITIVE_TTL_MS = 60_000L
        private const val NEGATIVE_TTL_MS = 10_000L
        private const val PROBE_TIMEOUT_MS = 1_500L
    }
}
