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

import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import com.vitorpamplona.quartz.utils.Log
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Records phase timings per call so we can see when media loads are slow
 * because of DNS, TCP, TLS, first-byte, or because the dispatcher queued
 * the call (i.e. we hit maxRequestsPerHost / maxRequests).
 *
 * Release builds log only slow calls, queued calls, and errors. Debug
 * builds log every completed call.
 */
class MediaCallEventListener(
    private val dispatcher: Dispatcher,
    private val connectionPool: ConnectionPool,
    private val dns: SurgeDns,
) : EventListener() {
    private var callStartNanos = 0L
    private var dnsStartNanos = 0L
    private var dnsElapsedMs = -1L
    private var connectStartNanos = 0L
    private var connectElapsedMs = -1L
    private var secureStartNanos = 0L
    private var secureElapsedMs = -1L
    private var responseHeadersNanos = 0L

    // stays true unless connectStart fires (a new connection was needed)
    private var connectionReused = true
    private var queuedAtStart = 0

    override fun callStart(call: Call) {
        callStartNanos = System.nanoTime()
        queuedAtStart = dispatcher.queuedCallsCount()
    }

    override fun dnsStart(
        call: Call,
        domainName: String,
    ) {
        dnsStartNanos = System.nanoTime()
    }

    override fun dnsEnd(
        call: Call,
        domainName: String,
        inetAddressList: List<InetAddress>,
    ) {
        dnsElapsedMs = (System.nanoTime() - dnsStartNanos) / 1_000_000
    }

    override fun connectStart(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
    ) {
        connectStartNanos = System.nanoTime()
        connectionReused = false
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        connectElapsedMs = (System.nanoTime() - connectStartNanos) / 1_000_000
    }

    override fun secureConnectStart(call: Call) {
        secureStartNanos = System.nanoTime()
    }

    override fun secureConnectEnd(
        call: Call,
        handshake: Handshake?,
    ) {
        secureElapsedMs = (System.nanoTime() - secureStartNanos) / 1_000_000
    }

    override fun responseHeadersStart(call: Call) {
        responseHeadersNanos = System.nanoTime()
    }

    override fun callEnd(call: Call) = finish(call, null)

    override fun callFailed(
        call: Call,
        ioe: IOException,
    ) = finish(call, ioe)

    private fun finish(
        call: Call,
        error: IOException?,
    ) {
        val host = call.request().url.host

        // The whole call failed (after OkHttp exhausted every address from the DNS lookup).
        // Drop the cached entry so the next attempt re-resolves instead of trying the same
        // dead IPs for up to 24h. Per-attempt connectFailed isn't enough — a multi-A-record
        // host can have one bad IP and OkHttp will recover by trying the next one.
        // EXCEPT when the failure is the DNS lookup itself: the freshly-written negative
        // entry IS the useful cache state, and deleting it would defeat the negative TTL
        // (see DnsInvalidatingEventListener).
        if (error != null && !error.causedByUnknownHost()) {
            dns.invalidate(host)
        }

        val totalMs = (System.nanoTime() - callStartNanos) / 1_000_000
        val isSlow = totalMs >= SLOW_CALL_THRESHOLD_MS
        val wasQueued = queuedAtStart > 0

        if (error == null && !isSlow && !wasQueued && !verboseLogging) return

        val ttfbMs = if (responseHeadersNanos > 0) (responseHeadersNanos - callStartNanos) / 1_000_000 else -1L
        val reuseTag = if (connectionReused) "reused" else "new"

        val msg =
            buildString {
                append(host)
                append(" total=").append(totalMs).append("ms")
                append(" ttfb=").append(ttfbMs).append("ms")
                append(" conn=").append(reuseTag)
                if (!connectionReused) {
                    if (dnsElapsedMs >= 0) append(" dns=").append(dnsElapsedMs).append("ms")
                    if (connectElapsedMs >= 0) append(" tcp=").append(connectElapsedMs).append("ms")
                    if (secureElapsedMs >= 0) append(" tls=").append(secureElapsedMs).append("ms")
                }
                if (wasQueued) {
                    append(" QUEUED(depth=").append(queuedAtStart)
                    append(" running=").append(dispatcher.runningCallsCount())
                    append(" pool=").append(connectionPool.connectionCount())
                    append('/').append(connectionPool.idleConnectionCount())
                    append(')')
                }
                if (error != null) append(" error=").append(error.javaClass.simpleName).append(':').append(error.message)
            }

        when {
            error != null -> Log.w(TAG, msg)
            isSlow || wasQueued -> Log.i(TAG, msg)
            else -> Log.d(TAG, msg)
        }
    }

    companion object {
        const val TAG = "MediaHttp"
        const val SLOW_CALL_THRESHOLD_MS = 1500L

        /**
         * When true, logs every completed call instead of only slow/queued/failed
         * ones. The Android app sets this from its BuildConfig at startup; other
         * front ends leave it off.
         */
        var verboseLogging: Boolean = false
    }
}

class MediaCallEventListenerFactory(
    private val dispatcher: Dispatcher,
    private val connectionPool: ConnectionPool,
    private val dns: SurgeDns,
) : EventListener.Factory {
    override fun create(call: Call): EventListener = MediaCallEventListener(dispatcher, connectionPool, dns)
}
