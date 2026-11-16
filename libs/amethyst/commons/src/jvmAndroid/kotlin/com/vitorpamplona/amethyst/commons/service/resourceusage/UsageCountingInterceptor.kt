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
package com.vitorpamplona.amethyst.commons.service.resourceusage

import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Request tag carrying the ledger subsystem a request belongs to. */
class UsageRoleTag(
    val role: String,
)

/**
 * Estimates cellular-radio wake-ups caused by HTTP traffic: a new burst is
 * counted whenever bytes flow after more than [BURST_GAP_MS] of HTTP silence,
 * approximating the radio having dropped to idle in between (LTE/5G inactivity
 * timers are typically ~10s). Bytes alone don't predict battery — many small
 * scattered requests cost far more than one continuous download of the same
 * size, because every burst pays the radio's ramp + tail energy. This counter
 * is what makes that pattern visible.
 *
 * Caveat: bursts are counted from HTTP activity only. While relays are
 * connected their traffic keeps the radio awake anyway — that cost is already
 * captured by the relay connection-time counters.
 */
class RadioBurstEstimator(
    private val accountant: ResourceUsageAccountant,
    private val isMobile: () -> Boolean,
    private val isForeground: () -> Boolean,
    private val nowMs: () -> Long = { elapsedRealtimeMillis() },
) {
    @Volatile private var lastActivityMs = Long.MIN_VALUE

    fun onHttpActivity() {
        val now = nowMs()
        val last = lastActivityMs
        lastActivityMs = now
        if (last == Long.MIN_VALUE || now - last > BURST_GAP_MS) {
            accountant.add(UsageKeys.radioBursts(isMobile(), isForeground()), 1)
        }
    }

    companion object {
        const val BURST_GAP_MS = 10_000L
    }
}

/**
 * Application interceptor that accounts every HTTP request into the ledger:
 * bytes up/down, request count, and active-transfer time, attributed to the
 * subsystem named by the request's [UsageRoleTag] (set by the per-role
 * clients from RoleBasedHttpClientBuilder) or to [defaultRole] for anything
 * that reaches the shared clients untagged — so no HTTP traffic can escape
 * the ledger.
 *
 * Installed FIRST on the base client (outermost), so the tagging interceptor
 * of role-wrapped clients must be inserted BEFORE it (see [HttpUsageMeter]).
 * Download bytes are counted as the app actually consumes the streamed body,
 * so cancelled loads count only what crossed to the app. Network/visibility
 * dims are sampled when bytes flow, matching what the radio actually did.
 */
class UsageCountingInterceptor(
    private val accountant: ResourceUsageAccountant,
    private val isMobile: () -> Boolean,
    private val isForeground: () -> Boolean,
    private val bursts: RadioBurstEstimator? = null,
    private val defaultRole: String = UsageKeys.ROLE_OTHER,
    private val nowMs: () -> Long = { elapsedRealtimeMillis() },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Loopback traffic never touches the radio: counting it would inflate the
        // network numbers — and could trip the background-data alert — for exactly
        // the users who cache aggressively to SAVE data. This interceptor is the
        // outermost one, so it sees URLs BEFORE LocalBlossomCacheRedirectInterceptor
        // rewrites them to 127.0.0.1; the host that was actually contacted is only
        // known from the response's request.
        if (isLoopback(request.url.host)) return chain.proceed(request)

        val role = request.tag(UsageRoleTag::class.java)?.role ?: defaultRole

        val startedAtMs = nowMs()
        val response =
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                countRequest(request, role)
                throw e
            }

        if (isLoopback(response.request.url.host)) return response

        countRequest(request, role)

        return response
            .newBuilder()
            .body(
                CountingResponseBody(
                    delegate = response.body,
                    onBytes = { bytes ->
                        accountant.add(UsageKeys.net(role, isMobile(), isForeground(), received = true), bytes)
                        bursts?.onHttpActivity()
                    },
                    onFinished = {
                        accountant.add(UsageKeys.netActiveMs(role, isMobile(), isForeground()), nowMs() - startedAtMs)
                    },
                ),
            ).build()
    }

    private fun countRequest(
        request: Request,
        role: String,
    ) {
        accountant.add(UsageKeys.netReqs(role, isMobile(), isForeground()), 1)
        bursts?.onHttpActivity()

        val requestBytes = request.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
        if (requestBytes > 0) {
            accountant.add(UsageKeys.net(role, isMobile(), isForeground(), received = false), requestBytes)
        }
    }

    companion object {
        /** OkHttp reports IPv6 hosts unbracketed ("::1"); keep the bracketed form defensively. */
        fun isLoopback(host: String): Boolean = host.startsWith("127.") || host == "localhost" || host == "::1" || host == "[::1]"
    }

    private class CountingResponseBody(
        private val delegate: ResponseBody,
        private val onBytes: (Long) -> Unit,
        onFinished: () -> Unit,
    ) : ResponseBody() {
        private val finished = AtomicBoolean(false)
        private val onFinishedOnce = {
            if (finished.compareAndSet(false, true)) onFinished()
        }

        override fun contentType() = delegate.contentType()

        override fun contentLength() = delegate.contentLength()

        private val countedSource by lazy {
            object : ForwardingSource(delegate.source() as Source) {
                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    val read = super.read(sink, byteCount)
                    if (read > 0) {
                        onBytes(read)
                    } else if (read == -1L) {
                        onFinishedOnce()
                    }
                    return read
                }

                override fun close() {
                    super.close()
                    onFinishedOnce()
                }
            }.buffer()
        }

        override fun source() = countedSource
    }
}

/**
 * Hands out per-subsystem OkHttp clients: the shared base client (which
 * carries the single [UsageCountingInterceptor]) wrapped with a tagging
 * interceptor inserted at position 0, OUTSIDE the counter, so the tag is
 * visible when the counter reads it. Wrapped clients are cached per
 * (role, base identity); base clients are rebuilt on proxy/network changes,
 * so the cache is cleared when it grows past a small bound.
 */
class HttpUsageMeter {
    private val wrapped = ConcurrentHashMap<Pair<String, OkHttpClient>, OkHttpClient>()

    fun counted(
        role: String,
        base: OkHttpClient,
    ): OkHttpClient {
        if (wrapped.size > MAX_CACHED) wrapped.clear()
        return wrapped.getOrPut(role to base) {
            base
                .newBuilder()
                .apply { interceptors().add(0, RoleTaggingInterceptor(role)) }
                .build()
        }
    }

    private class RoleTaggingInterceptor(
        private val role: String,
    ) : Interceptor {
        private val tag = UsageRoleTag(role)

        override fun intercept(chain: Interceptor.Chain): Response =
            chain.proceed(
                chain
                    .request()
                    .newBuilder()
                    .tag(UsageRoleTag::class.java, tag)
                    .build(),
            )
    }

    companion object {
        // roles (8) x live base clients (proxy on/off ~2) with headroom for
        // network-change rebuilds before the clear kicks in.
        private const val MAX_CACHED = 32
    }
}
