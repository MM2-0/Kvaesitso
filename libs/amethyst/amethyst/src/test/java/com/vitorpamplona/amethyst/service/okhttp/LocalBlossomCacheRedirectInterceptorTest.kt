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

import com.vitorpamplona.amethyst.commons.service.http.LocalBlossomCacheRedirectInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class LocalBlossomCacheRedirectInterceptorTest {
    private val sha = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"

    @Test
    fun bridgeOffPassesThrough() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { false }
        val captured = mutableListOf<String>()
        val response = run(interceptor, "https://blossom.example.com/$sha.jpg", captured)
        assertEquals("https://blossom.example.com/$sha.jpg", captured.single())
        response.close()
    }

    @Test
    fun bridgeOnRewritesShaInPath() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = run(interceptor, "https://blossom.example.com/$sha.jpg", captured)
        assertEquals(
            "http://127.0.0.1:24242/$sha.jpg?xs=https%3A%2F%2Fblossom.example.com",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnPreservesNonHexUrls() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = run(interceptor, "https://example.com/avatar.jpg", captured)
        assertEquals("https://example.com/avatar.jpg", captured.single())
        response.close()
    }

    @Test
    fun bridgeOnSkipsLocalhost() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = run(interceptor, "http://127.0.0.1:24242/$sha.jpg", captured)
        assertEquals("http://127.0.0.1:24242/$sha.jpg", captured.single())
        response.close()
    }

    @Test
    fun bridgeOnHandlesNonStandardPort() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = run(interceptor, "https://blossom.example.com:8443/$sha.png", captured)
        assertEquals(
            "http://127.0.0.1:24242/$sha.png?xs=https%3A%2F%2Fblossom.example.com%3A8443",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnHandlesUppercaseSha() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = run(interceptor, "https://blossom.example.com/${sha.uppercase()}.jpg", captured)
        assertEquals(
            "http://127.0.0.1:24242/$sha.jpg?xs=https%3A%2F%2Fblossom.example.com",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnFallsBackToBinExtension() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = run(interceptor, "https://blossom.example.com/$sha", captured)
        assertEquals(
            "http://127.0.0.1:24242/$sha.bin?xs=https%3A%2F%2Fblossom.example.com",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnHandlesShaInDeeperPathSegment() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = run(interceptor, "https://nostr.build/i/cache/$sha.webp", captured)
        assertEquals(
            "http://127.0.0.1:24242/$sha.webp?xs=https%3A%2F%2Fnostr.build%2Fi%2Fcache",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnPreservesNostrBuildPathPrefix() {
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response = run(interceptor, "https://cdn.nostr.build/i/$sha.jpg", captured)
        assertEquals(
            "http://127.0.0.1:24242/$sha.jpg?xs=https%3A%2F%2Fcdn.nostr.build%2Fi",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnRewritesShaInLastPathSegmentWithHexPrefix() {
        // share.yabu.me layout: <cache-prefix-sha>/<blob-sha>.<ext>
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val response =
            run(
                interceptor,
                "https://share.yabu.me/84b0c46ab699ac35eb2ca286470b85e081db2087cdef63932236c397417782f5/28fa4d999af6ae3e4e11bfc2727130ef1b3a13cc0f981e5a93c3996cb2f524e5.webp",
                captured,
            )
        assertEquals(
            "http://127.0.0.1:24242/28fa4d999af6ae3e4e11bfc2727130ef1b3a13cc0f981e5a93c3996cb2f524e5.webp?xs=https%3A%2F%2Fshare.yabu.me%2F84b0c46ab699ac35eb2ca286470b85e081db2087cdef63932236c397417782f5",
            captured.single(),
        )
        response.close()
    }

    @Test
    fun bridgeOnSkipsWhenLastSegmentIsNotSha() {
        // Per BUD-01 the last segment is the blob; if it isn't a sha256, the
        // URL isn't a Blossom blob even if an earlier segment is hex.
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val url = "https://example.com/$sha/avatar.jpg"
        val response = run(interceptor, url, captured)
        assertEquals(url, captured.single())
        response.close()
    }

    @Test
    fun bridgeOnSkipsNostrBuildResizedVariants() {
        // sending xs=https://nostr.build/i would 404 because the real blob
        // is at /i/nostr.build_<sha>.jpg, not /i/<sha>.
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val url = "https://nostr.build/i/nostr.build_$sha.jpg"
        val response = run(interceptor, url, captured)
        assertEquals(url, captured.single())
        response.close()
    }

    @Test
    fun bridgeOnSkipsWhenLastSegmentHasSuffixAfterSha() {
        // A filename like <sha>_thumb.jpg isn't a BUD-01 blob URL either.
        val interceptor = LocalBlossomCacheRedirectInterceptor { true }
        val captured = mutableListOf<String>()
        val url = "https://example.com/${sha}_thumb.jpg"
        val response = run(interceptor, url, captured)
        assertEquals(url, captured.single())
        response.close()
    }

    /**
     * Runs [interceptor] against [url] and returns its response. The chain is a
     * dynamic proxy that only implements [Interceptor.Chain.request] and
     * [Interceptor.Chain.proceed]; `proceed` records the URL it is asked to
     * fetch — i.e. the URL after any rewrite [interceptor] applied — and returns
     * a synthetic 200 without touching the network. A proxy is used instead of
     * an `object :` literal because OkHttp's `Interceptor.Chain` declares ~40
     * members (client-config getters and `withX` reconfigurers) that this test
     * never exercises and that would otherwise need empty stubs.
     */
    private fun run(
        interceptor: Interceptor,
        url: String,
        captured: MutableList<String>,
    ): Response = interceptor.intercept(fakeChain(url, captured))

    private fun fakeChain(
        url: String,
        captured: MutableList<String>,
    ): Interceptor.Chain {
        // Marked the way the media loaders mark their downloads; only those are bridged.
        val request =
            Request
                .Builder()
                .url(url.toHttpUrl())
                .header(LocalBlossomCacheRedirectInterceptor.MEDIA_HEADER, LocalBlossomCacheRedirectInterceptor.MEDIA)
                .build()
        return Proxy.newProxyInstance(
            Interceptor.Chain::class.java.classLoader,
            arrayOf(Interceptor.Chain::class.java),
        ) { _, method, args ->
            when (method.name) {
                "request" -> request
                "proceed" -> {
                    val proceeded = args[0] as Request
                    captured.add(proceeded.url.toString())
                    Response
                        .Builder()
                        .request(proceeded)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("".toResponseBody(null))
                        .build()
                }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Interceptor.Chain
    }
}
