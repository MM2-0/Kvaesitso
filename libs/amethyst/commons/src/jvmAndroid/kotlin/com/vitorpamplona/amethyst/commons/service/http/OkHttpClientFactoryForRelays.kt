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
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.TcpNoDelaySocketFactory
import com.vitorpamplona.quartz.utils.Log
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.ExecutorService

/**
 * @param dispatcherExecutor runs OkHttp's async calls. A relay websocket's reader loop lives
 * inside its call, so every open socket holds one of this executor's threads for its whole
 * life. Null keeps OkHttp's default (a platform thread per socket), which Android needs: ART
 * has no JVM virtual threads. JVM-only callers (desktop, `amy`) pass a virtual-thread
 * executor so a large relay set doesn't hold a platform thread per socket or serialize its
 * dials behind `Thread.start`.
 */
class OkHttpClientFactoryForRelays(
    userAgent: String,
    private val dns: SurgeDns,
    private val onionCache: OnionLocationCache,
    dispatcherExecutor: ExecutorService? = null,
) {
    companion object {
        // by picking a random proxy port, the connection will fail as it should.
        const val DEFAULT_SOCKS_PORT: Int = 9050
        const val DEFAULT_IS_MOBILE: Boolean = false
        const val DEFAULT_TIMEOUT_ON_WIFI_SECS: Int = 10
        const val DEFAULT_TIMEOUT_ON_MOBILE_SECS: Int = 30

        // 120s is a measured sweet spot, not a guess — see
        // amethyst/plans/2026-07-12-relay-ping-interval-study.md (probe of 122
        // production relays). Idle-timeout tiers observed in production are
        // ~60s / ~120s / ~240s / ~300s / ~600s, and a client ping only reliably
        // resets a relay's idle timer when the interval sits well BELOW the
        // tier (240s pings already lose the ~240s and ~300s tiers, incl. every
        // nostr1.com-hosted relay; 300s pings lose relay.snort.social). Raising
        // the interval also saves almost no battery: 90% of surveyed relays
        // send their own server pings every 30-70s, which OkHttp must answer,
        // so the radio's wake cadence is set by the relays, not by this value.
        // Lowering it would only rescue the ~120s tier (6/122 relays, already
        // cycling today) at 2x the ping traffic on every other connection.
        const val WEBSOCKET_PING_INTERVAL_SECS: Long = 120
    }

    val myDispatcher =
        (if (dispatcherExecutor != null) Dispatcher(dispatcherExecutor) else Dispatcher()).apply {
            if (!HttpClientEnvironment.isEmulator) {
                maxRequestsPerHost = 10
                maxRequests = 1024
            } else {
                maxRequestsPerHost = 5
                maxRequests = 256
                Log.i("OkHttpClientFactory", "Emulator detected, using default maxRequests: 64.")
            }
        }

    private val rootClient =
        OkHttpClient
            .Builder()
            .dispatcher(myDispatcher)
            // TCP_NODELAY: a CLOSE (never answered by relays) followed by a
            // REQ — every feed switch — otherwise nagles the REQ behind the
            // unACKed CLOSE for the peer's delayed-ACK window (~40 ms+).
            // See quartz TcpNoDelaySocketFactory. Direct connections only;
            // the Tor SOCKS path is unaffected.
            .socketFactory(TcpNoDelaySocketFactory)
            .dns(dns)
            .eventListenerFactory(DnsInvalidatingEventListener.Factory(dns))
            .followRedirects(true)
            .followSslRedirects(true)
            // First, so it wraps every interceptor below and the connection step: keeps a
            // malformed host from crashing the app on the Dispatcher thread.
            .addInterceptor(RuntimeToIoExceptionInterceptor())
            .addInterceptor(DefaultContentTypeInterceptor(userAgent))
            .addInterceptor(OnionLocationInterceptor(onionCache))
            .build()

    // No connection-pool eviction when the proxy changes. OkHttp's `Address` -- the
    // pool's lookup key -- includes the proxy (`Address.equalsNonHost`), so a call
    // is only ever handed a connection opened through the very same route. A
    // connection left over from an old proxy is already unreachable and simply ages
    // out of the pool; evicting was defensive, not load-bearing.
    //
    // It also cost more than it looked. `evictAll()` empties the ENTIRE shared pool,
    // and this one factory mints both the proxied and the direct client (see
    // [DualHttpClientManagerForRelays]) -- `buildLocalSocksProxy` never returns null, so those
    // two alternated a single "last proxy" field forever. Every rebuild read as a
    // route change and dropped every warm connection the other client was using, on
    // each network-state emission and each resubscribe.
    fun buildHttpClient(
        proxy: Proxy?,
        timeoutSeconds: Int,
    ): OkHttpClient {
        val seconds = if (proxy != null) timeoutSeconds * 3 else timeoutSeconds
        return rootClient
            .newBuilder()
            .proxy(proxy)
            .apply { if (proxy != null) addInterceptor(OnionUrlRewriteInterceptor(onionCache)) }
            .connectTimeout(Duration.ofSeconds(seconds.toLong()))
            .readTimeout(Duration.ofSeconds(seconds.toLong() * 3))
            .writeTimeout(Duration.ofSeconds(seconds.toLong() * 3))
            .pingInterval(Duration.ofSeconds(WEBSOCKET_PING_INTERVAL_SECS))
            .build()
    }

    /**
     * Closes every idle pooled connection. Call only on a real proxy-route change (see
     * [evictOnProxyRouteChange]) — connections on a dead route are already unreachable, so this
     * is hygiene, not correctness, and it empties the pool BOTH clients share.
     */
    fun evictPooledConnections() = rootClient.connectionPool.evictAll()

    fun buildHttpClient(
        localSocksProxyPort: Int?,
        isMobile: Boolean?,
    ): OkHttpClient =
        buildHttpClient(
            buildLocalSocksProxy(localSocksProxyPort),
            buildTimeout(isMobile ?: DEFAULT_IS_MOBILE),
        )

    fun buildHttpClient(isMobile: Boolean?): OkHttpClient =
        buildHttpClient(
            null,
            buildTimeout(isMobile ?: DEFAULT_IS_MOBILE),
        )

    fun buildTimeout(isMobile: Boolean): Int =
        if (isMobile) {
            DEFAULT_TIMEOUT_ON_MOBILE_SECS
        } else {
            DEFAULT_TIMEOUT_ON_WIFI_SECS
        }

    fun buildLocalSocksProxy(port: Int?) = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port ?: DEFAULT_SOCKS_PORT))
}
