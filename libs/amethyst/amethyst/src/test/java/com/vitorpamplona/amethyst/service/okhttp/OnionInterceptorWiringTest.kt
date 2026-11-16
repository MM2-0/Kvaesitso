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
package com.vitorpamplona.amethyst.service.okhttp

import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactory
import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactoryForRelays
import com.vitorpamplona.amethyst.commons.service.http.OnionLocationCache
import com.vitorpamplona.amethyst.commons.service.http.OnionLocationInterceptor
import com.vitorpamplona.amethyst.commons.service.http.OnionUrlRewriteInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Pins the Onion-Location interceptor behavior the entire app's onion-routing
 * story relies on.
 *
 * Onion-Location is a free passive discovery mechanism: an `.onion`-equipped server
 * advertises its onion address via an HTTP response header, and the app caches the
 * mapping for later Tor-routed connections. The mechanism only works if **every**
 * OkHttp client built by the app installs the interceptors:
 *
 * - [OnionLocationInterceptor] on every client (no-proxy AND Tor-proxied) so we capture
 *   the header from whichever client happened to make the request.
 * - [OnionUrlRewriteInterceptor] on Tor-proxied clients only (clearnet clients must never
 *   try to resolve `.onion`).
 *
 * Wiring is enforced by [OkHttpClientFactory] and [OkHttpClientFactoryForRelays] taking
 * the cache as a **non-nullable** constructor parameter and unconditionally adding the
 * interceptors in their builder chain. Factory-instantiation in unit tests is blocked by
 * OkHttp 5's Android platform shim (it calls `android.util.Log.isLoggable`, which only
 * exists on a real device), so the wiring is pinned at the type level rather than via
 * reflective inspection of a built client. This suite covers the interceptors themselves
 * and the cache they share so any regression in their behavior — missing put, wrong
 * rewrite scheme, stale TTL, etc. — fails fast.
 *
 * The chain is a [Proxy] that only implements [Interceptor.Chain.request] and
 * [Interceptor.Chain.proceed]; same pattern as [LocalBlossomCacheRedirectInterceptorTest].
 */
class OnionInterceptorWiringTest {
    // --- OnionLocationInterceptor: passively captures the header ---

    @Test
    fun locationInterceptor_recordsHeaderUnderClearnetHost() {
        val cache = OnionLocationCache()
        val interceptor = OnionLocationInterceptor(cache)

        runWithResponse(
            interceptor = interceptor,
            url = "https://example.com/some/path",
            responseHeaders = mapOf("Onion-Location" to "http://abcdef.onion/"),
        )

        assertEquals(
            "Cache key must be the original clearnet host, not the .onion — otherwise " +
                "the rewriter on the next request looks up a key that was never written.",
            "http://abcdef.onion/",
            cache.get("example.com"),
        )
    }

    @Test
    fun locationInterceptor_websocketUpgrade_alsoRecords() {
        // The Onion-Location header is captured from any response — including the
        // WebSocket 101 Switching Protocols upgrade handshake on a Nostr relay
        // connection. This is the primary discovery path for Tor-routed relays.
        // (OkHttp normalizes ws/wss to http/https on Request.url, so even though
        // the relay URL is `wss://`, the interceptor sees `https://`. The cache
        // entry is keyed by that normalized host, which is what the rewriter
        // later looks up on the next outbound `wss://` request.)
        val cache = OnionLocationCache()
        val interceptor = OnionLocationInterceptor(cache)

        runWithResponse(
            interceptor = interceptor,
            url = "https://relay.example/",
            responseCode = 101,
            responseHeaders = mapOf("Onion-Location" to "http://relay.onion/"),
        )

        assertEquals("http://relay.onion/", cache.get("relay.example"))
    }

    @Test
    fun locationInterceptor_noHeader_writesNothing() {
        val cache = OnionLocationCache()
        val interceptor = OnionLocationInterceptor(cache)

        runWithResponse(
            interceptor = interceptor,
            url = "https://example.com/",
            responseHeaders = emptyMap(),
        )

        assertNull(
            "A response with no Onion-Location must not pollute the cache.",
            cache.get("example.com"),
        )
    }

    // --- OnionUrlRewriteInterceptor: rewrites outbound URLs to known .onions ---

    @Test
    fun rewriteInterceptor_unknownHost_passesThrough() {
        val cache = OnionLocationCache()
        val interceptor = OnionUrlRewriteInterceptor(cache)

        val captured = captureRewrite(interceptor, "https://example.com/")

        assertEquals(
            "Unknown hosts must reach the original URL untouched.",
            "https://example.com/",
            captured,
        )
    }

    @Test
    fun rewriteInterceptor_https_to_httpsOnion_keepsHttps() {
        val cache = OnionLocationCache().apply { put("example.com", "https://abc.onion/") }
        val interceptor = OnionUrlRewriteInterceptor(cache)

        val captured = captureRewrite(interceptor, "https://example.com/foo")
        val url = captured.toHttpUrl()

        assertEquals("abc.onion", url.host)
        assertEquals("https", url.scheme)
        assertEquals("/foo", url.encodedPath)
    }

    @Test
    fun rewriteInterceptor_https_to_httpOnion_downgradesToHttp() {
        // An onion service advertising http:// is not a real downgrade: the Tor
        // hidden-service descriptor already provides authenticated end-to-end
        // encryption. We keep the request in the http protocol family.
        val cache = OnionLocationCache().apply { put("example.com", "http://abc.onion:8080/") }
        val interceptor = OnionUrlRewriteInterceptor(cache)

        val url = captureRewrite(interceptor, "https://example.com/").toHttpUrl()

        assertEquals("http", url.scheme)
        assertEquals(8080, url.port)
    }

    // Note: OkHttp normalizes `ws/wss` URLs to `http/https` on `Request.url`
    // before any interceptor runs, so the `ws/wss` branches in
    // OnionUrlRewriteInterceptor.intercept() are defensive code only —
    // unreachable from a real OkHttp call site. Tests of them would have to
    // construct an HttpUrl with a ws/wss scheme, which the OkHttp 5 API
    // refuses to parse. The behavior we DO need to pin is that the http/https
    // path keeps the protocol family across the onion swap, which the
    // `rewriteInterceptor_https_to_*` cases above already cover.

    @Test
    fun rewriteInterceptor_unparseableOnionUrl_passesThrough() {
        // Malformed cache entries must not break connectivity — fall back to original.
        val cache = OnionLocationCache().apply { put("example.com", "not a url") }
        val interceptor = OnionUrlRewriteInterceptor(cache)

        val captured = captureRewrite(interceptor, "https://example.com/")
        assertEquals("https://example.com/", captured)
    }

    // --- OnionLocationCache: TTL + shared identity ---

    @Test
    fun cache_putGet_roundTripsValue() {
        val cache = OnionLocationCache()
        cache.put("example.com", "http://abc.onion/")
        assertEquals("http://abc.onion/", cache.get("example.com"))
    }

    @Test
    fun cache_unknownHost_returnsNull() {
        val cache = OnionLocationCache()
        assertNull(cache.get("never-seen.example"))
    }

    @Test
    fun cache_isSharedAcrossInterceptorPair() {
        // Critical invariant: an entry the location interceptor writes must be
        // visible to the rewriter on the next request. Both interceptors close
        // over the SAME cache instance — nothing fancier than object identity.
        val cache = OnionLocationCache()
        val location = OnionLocationInterceptor(cache)
        val rewriter = OnionUrlRewriteInterceptor(cache)

        // 1. First response advertises an onion.
        runWithResponse(
            interceptor = location,
            url = "https://example.com/",
            responseHeaders = mapOf("Onion-Location" to "http://abc.onion/"),
        )

        // 2. A subsequent request to the same host hits the rewriter and goes to .onion.
        val capturedUrl = captureRewrite(rewriter, "https://example.com/api/v1").toHttpUrl()

        assertEquals("abc.onion", capturedUrl.host)
        assertEquals(
            "Path must be preserved across the rewrite.",
            "/api/v1",
            capturedUrl.encodedPath,
        )
        assertNotEquals(
            "Host must have changed.",
            "example.com",
            capturedUrl.host,
        )
    }

    // --- Compile-time pin: the interceptors must remain OkHttp [Interceptor]s ---

    @Test
    fun interceptorClasses_remainOkHttpInterceptors() {
        val cache = OnionLocationCache()

        // Assigning to an `Interceptor` reference is the compile-time pin: a future
        // refactor that drops the interface from either class breaks this build,
        // because addInterceptor() in both factories takes Interceptor.
        @Suppress("UNUSED_VARIABLE")
        val a: Interceptor = OnionLocationInterceptor(cache)

        @Suppress("UNUSED_VARIABLE")
        val b: Interceptor = OnionUrlRewriteInterceptor(cache)
    }

    // --- Test helpers ---

    /**
     * Runs [interceptor] against a request to [url], having `proceed` return a synthetic
     * response with [responseCode] / [responseHeaders]. Returns the URL that
     * `proceed` was ultimately called with (so rewrite tests can read it).
     */
    private fun runWithResponse(
        interceptor: Interceptor,
        url: String,
        responseCode: Int = 200,
        responseHeaders: Map<String, String> = emptyMap(),
    ): String {
        val captured = mutableListOf<String>()
        val chain = fakeChain(url, captured, responseCode, responseHeaders)
        interceptor.intercept(chain).close()
        return captured.single()
    }

    /**
     * Convenience for rewrite-interceptor tests: returns the URL that `proceed`
     * was called with (i.e. the rewritten URL, or the original if no rewrite).
     */
    private fun captureRewrite(
        interceptor: Interceptor,
        url: String,
    ): String = runWithResponse(interceptor, url)

    /**
     * A dynamic-proxy chain that implements only [Interceptor.Chain.request] and
     * [Interceptor.Chain.proceed]. Used because OkHttp 5's `Chain` declares ~40
     * members (client-config getters and `withX` reconfigurers) that these tests
     * never exercise.
     */
    private fun fakeChain(
        url: String,
        captured: MutableList<String>,
        responseCode: Int,
        responseHeaders: Map<String, String>,
    ): Interceptor.Chain {
        val request = Request.Builder().url(url.toHttpUrl()).build()
        return Proxy.newProxyInstance(
            Interceptor.Chain::class.java.classLoader,
            arrayOf(Interceptor.Chain::class.java),
        ) { _, method, args ->
            when (method.name) {
                "request" -> request
                "proceed" -> {
                    val proceeded = args[0] as Request
                    captured.add(proceeded.url.toString())
                    val builder =
                        Response
                            .Builder()
                            .request(proceeded)
                            .protocol(Protocol.HTTP_1_1)
                            .code(responseCode)
                            .message(if (responseCode == 101) "Switching Protocols" else "OK")
                            .body("".toResponseBody(null))
                    responseHeaders.forEach { (k, v) -> builder.header(k, v) }
                    builder.build()
                }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Interceptor.Chain
    }
}
