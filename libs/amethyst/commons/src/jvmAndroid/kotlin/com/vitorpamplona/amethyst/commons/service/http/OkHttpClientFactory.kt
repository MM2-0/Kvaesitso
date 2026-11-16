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

import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactoryForRelays.Companion.DEFAULT_IS_MOBILE
import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactoryForRelays.Companion.DEFAULT_SOCKS_PORT
import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactoryForRelays.Companion.DEFAULT_TIMEOUT_ON_MOBILE_SECS
import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactoryForRelays.Companion.DEFAULT_TIMEOUT_ON_WIFI_SECS
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Builder for the general-purpose (non-relay) OkHttp client used by image
 * downloads, NIP-96/Blossom uploads, NIP-05 lookups, LNURL/money, link
 * previews, push registration, etc. Each [DualHttpClientManager] holds one
 * of these and rebuilds clients on Tor proxy / mobile-data changes.
 *
 * [OnionLocationCache] is **required**: every OkHttp client minted by this
 * factory installs [OnionLocationInterceptor] on the no-proxy and Tor-proxied
 * variants so any HTTP/WebSocket response that carries an `Onion-Location`
 * header (NIP-11 docs, image hosts, blossom servers, NIP-96 endpoints, LNURL
 * providers, anything) populates the cache. Tor-proxied clients additionally
 * install [OnionUrlRewriteInterceptor] so subsequent requests are rerouted
 * to the cached `.onion` and avoid exit nodes entirely. Treating the cache as
 * required (not nullable) prevents a future call site from silently disabling
 * onion-routing for an entire HTTP role.
 */
class OkHttpClientFactory(
    keyCache: EncryptionKeyCache,
    val userAgent: String,
    private val dns: SurgeDns,
    /**
     * Returns `true` when a sha256-keyed HTTP request should be transparently
     * rewritten to the local Blossom cache, given whether it is a profile picture
     * (master toggle on, probe up, and either not restricted to profile pictures
     * or this is one). When `null`, the interceptor is disabled — useful for
     * tests or pre-configuration call sites.
     */
    val shouldBridgeBlossomCache: ((profilePicture: Boolean) -> Boolean)? = null,
    private val onionCache: OnionLocationCache,
    /**
     * Resource-usage ledger counter, installed OUTERMOST on the shared base
     * client so every request through any derived client is accounted —
     * per-role tagging wrappers only relabel, they never bypass. Null in
     * tests / pre-configuration call sites.
     */
    private val usageInterceptor: Interceptor? = null,
    /**
     * Retries auth-gated Blossom blob downloads with a signed BUD-01 `t=get`
     * token when the host answers `401` (e.g. Buzz's private media relay). Null
     * in tests / pre-configuration call sites, in which case such downloads stay
     * anonymous. See [BlossomReadAuthInterceptor].
     */
    private val blossomReadAuth: Interceptor? = null,
    /**
     * Called when the local Blossom cache refuses a connection, so the caller can
     * mark it unavailable instead of waiting for the next periodic probe.
     */
    onLocalBlossomCacheUnreachable: () -> Unit = {},
) {
    // val logging = LoggingInterceptor()
    val keyDecryptor = EncryptedBlobInterceptor(keyCache)

    // Tor-routed media skips the local cache (the cache would fetch the origin outside Tor, and
    // Tor refuses 127.0.0.1 anyway), but its media marker header must still be stripped: this
    // non-bridging instance takes the redirect's place on Tor clients, and stands in for it when
    // the bridge isn't configured at all.
    private val blossomCacheMarkerStripper = LocalBlossomCacheRedirectInterceptor(bridges = false) { false }

    private val blossomCacheRedirect =
        shouldBridgeBlossomCache?.let { LocalBlossomCacheRedirectInterceptor(keyCache, onLocalBlossomCacheUnreachable, bridges = true, shouldBridge = it) }
            ?: blossomCacheMarkerStripper

    // Most images/videos in a feed come from a small set of hosts (e.g. a single
    // Blossom/imgproxy server). OkHttp's default dispatcher caps inflight requests
    // per host at 5, which serializes feed loading. Raise the limits so the feed
    // can parallelize downloads the way a browser does.
    //
    // Resist trimming these on intuition. `maxRequests` is effectively the thread
    // ceiling (Dispatcher's executor is corePoolSize=0 / maxPoolSize=MAX_VALUE over
    // a SynchronousQueue), which makes a lower number look free -- but blocked
    // threads commit little, these hosts are HTTP/2 so concurrent calls to one host
    // multiplex over a single connection rather than a handshake each, and
    // `readyAsyncCalls` is strict FIFO with no priority. PrefetchFeedMedia enqueues
    // notes BEFORE the user reaches them, so a tighter cap makes the image actually
    // on screen queue behind those prefetches instead of starting straight away.
    // Change these with a benchmark/ run, not a hunch.
    private val dispatcher =
        Dispatcher().apply {
            if (!HttpClientEnvironment.isEmulator) {
                maxRequestsPerHost = 16
                maxRequests = 128
            } else {
                maxRequestsPerHost = 5
                maxRequests = 64
            }
        }

    // Keep more HTTP/2 connections warm so scrolling doesn't repeatedly re-TLS
    // to the same media host.
    private val connectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

    private val rootClient =
        OkHttpClient
            .Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .dns(dns)
            .eventListenerFactory(MediaCallEventListenerFactory(dispatcher, connectionPool, dns))
            // Some media hosts (e.g. blossom.primal.net) silently drop idle HTTP/2
            // connections between the bursts of a feed scroll. Without a keepalive
            // ping, OkHttp can pull such a dead connection from the 5-min pool and
            // the request stalls until the read timeout (30s wifi / 90s mobile),
            // which shows up as "the first image after a pause takes forever".
            // An HTTP/2 ping detects the dead connection in seconds and lets
            // retryOnConnectionFailure re-issue the request on a fresh one.
            .pingInterval(Duration.ofSeconds(HTTP2_PING_INTERVAL_SECS))
            .followRedirects(true)
            .followSslRedirects(true)
            // First, so it wraps every interceptor below and the connection step: keeps a
            // malformed host from crashing the app on the Dispatcher thread.
            .addInterceptor(RuntimeToIoExceptionInterceptor())
            .apply { usageInterceptor?.let { addInterceptor(it) } }
            .addInterceptor(DefaultContentTypeInterceptor(userAgent))
            // Sits before the local-cache redirect and outside the network interceptors, so it
            // sees the origin host (a token is scoped to it) and its retry re-runs the rest of
            // the stack (blossom cache, key decryptor) for the authenticated response. Only
            // signs on an actual 401. A request carrying a token is never bridged.
            .apply {
                blossomReadAuth?.let { addInterceptor(it) }
            }.addInterceptor(blossomCacheRedirect)
            // .addNetworkInterceptor(logging)
            .addNetworkInterceptor(keyDecryptor)
            // Passively populates [onionCache] from any HTTP/WebSocket response
            // that carries an `Onion-Location` header. Application-scoped so the
            // cache key is always the original clearnet host (see kdoc on
            // [OnionLocationInterceptor]).
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
    // [DualHttpClientManager]) -- `buildLocalSocksProxy` never returns null, so those
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
            // Only the Tor-routed variant rewrites outbound URLs to known
            // `.onion`s — clearnet clients must never try to resolve `.onion`
            // (DNS would fail, and we don't want fingerprintable lookups).
            .apply { if (proxy != null) addInterceptor(OnionUrlRewriteInterceptor(onionCache)) }
            // The local Blossom cache lives on 127.0.0.1, which Tor refuses to reach, and it
            // would fetch the origin outside Tor. Tor-routed media keeps going to its origin
            // through Tor; the local cache is used by the direct client only.
            .apply {
                if (proxy != null) {
                    val index = interceptors().indexOf(blossomCacheRedirect)
                    if (index >= 0) interceptors()[index] = blossomCacheMarkerStripper
                }
            }.connectTimeout(Duration.ofSeconds(seconds.toLong()))
            .readTimeout(Duration.ofSeconds(seconds.toLong() * 3))
            .writeTimeout(Duration.ofSeconds(seconds.toLong() * 3))
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

    companion object {
        // HTTP/2 keepalive ping for pooled media connections. Short enough to
        // evict a silently-dropped connection well before the read timeout would
        // otherwise stall a request for the full 30s/90s.
        const val HTTP2_PING_INTERVAL_SECS: Long = 10
    }
}
