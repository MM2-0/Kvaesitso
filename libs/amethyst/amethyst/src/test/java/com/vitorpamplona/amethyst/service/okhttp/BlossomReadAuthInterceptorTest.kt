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

import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthInterceptor
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class BlossomReadAuthInterceptorTest {
    private val sha = "2c5287a55cc550c9d6bc4206a4663900e083315f4a544ea3bc189e43dc330af6"
    private val host = "nosfabrica.communities.buzz.xyz"

    // --- blossomHashOrNull ------------------------------------------------

    @Test
    fun hashParsedFromPlainBlob() {
        assertEquals(sha, BlossomReadAuthInterceptor.blossomHashOrNull("/media/$sha.png"))
    }

    @Test
    fun hashParsedFromThumbnailVariant() {
        assertEquals(sha, BlossomReadAuthInterceptor.blossomHashOrNull("/media/$sha.thumb.jpg"))
    }

    @Test
    fun hashParsedWithoutExtension() {
        assertEquals(sha, BlossomReadAuthInterceptor.blossomHashOrNull("/$sha"))
    }

    @Test
    fun hashLowercasedFromUppercaseSegment() {
        assertEquals(sha, BlossomReadAuthInterceptor.blossomHashOrNull("/${sha.uppercase()}.png"))
    }

    @Test
    fun nonBlobPathsReturnNull() {
        assertNull(BlossomReadAuthInterceptor.blossomHashOrNull("/media/avatar.png"))
        assertNull(BlossomReadAuthInterceptor.blossomHashOrNull("/media/${sha}a.png"))
        assertNull(BlossomReadAuthInterceptor.blossomHashOrNull("/"))
    }

    // --- intercept behavior ----------------------------------------------
    //
    // The interceptor no longer performs the signed retry: waiting for a
    // signature on an OkHttp dispatcher thread held one of the 16 per-host
    // slots. It now returns the 401 and asks for a token to be minted
    // off-thread; BlossomReadAuthFetcher does the retry from a coroutine.

    @Test
    fun learnsHostAndAsksForATokenOn401() {
        val provider = RecordingProvider(cached = null)
        val chain = fakeChain("https://$host/media/$sha.png", codes = listOf(401))

        val response = provider.interceptor().intercept(chain.asChain())

        assertEquals("the 401 is surfaced for the fetcher to retry", 401, response.code)
        assertEquals("the interceptor must not retry itself", 1, chain.requests.size)
        assertNull("the only attempt is anonymous", chain.requests[0].header("Authorization"))
        assertEquals(host, provider.warmed.single())
        response.close()
    }

    @Test
    fun thumbnailUrlAlsoAsksForAToken() {
        val provider = RecordingProvider(cached = null)
        val chain = fakeChain("https://$host/media/$sha.thumb.jpg", codes = listOf(401))

        provider.interceptor().intercept(chain.asChain()).close()

        assertEquals(host, provider.warmed.single())
    }

    @Test
    fun successfulRequestNeverSigns() {
        val provider = RecordingProvider(cached = "Nostr token")
        val chain = fakeChain("https://blossom.example.com/$sha.png", codes = listOf(200))

        val response = provider.interceptor().intercept(chain.asChain())

        assertEquals(200, response.code)
        assertEquals(1, chain.requests.size)
        assertTrue("public host must not be signed", provider.warmed.isEmpty())
        response.close()
    }

    @Test
    fun nonBlobUrlNeverSignsEvenOn401() {
        val provider = RecordingProvider(cached = "Nostr token")
        val chain = fakeChain("https://example.com/media/avatar.png", codes = listOf(401))

        val response = provider.interceptor().intercept(chain.asChain())

        assertEquals(401, response.code)
        assertEquals(1, chain.requests.size)
        assertTrue(provider.warmed.isEmpty())
        response.close()
    }

    @Test
    fun requestWithExistingAuthPassesThrough() {
        val provider = RecordingProvider(cached = "Nostr token")
        val chain = fakeChain("https://$host/media/$sha.png", codes = listOf(401), preAuthHeader = "Nostr existing")

        val response = provider.interceptor().intercept(chain.asChain())

        assertEquals(401, response.code)
        assertEquals(1, chain.requests.size)
        assertTrue(provider.warmed.isEmpty())
        response.close()
    }

    @Test
    fun nonGetRequestPassesThrough() {
        val provider = RecordingProvider(cached = "Nostr token")
        val chain = fakeChain("https://$host/media/$sha.png", codes = listOf(401), method = "PUT")

        val response = provider.interceptor().intercept(chain.asChain())

        assertEquals(401, response.code)
        assertEquals(1, chain.requests.size)
        assertTrue(provider.warmed.isEmpty())
        response.close()
    }

    @Test
    fun learnsHostThenSignsSubsequentBlobsUpFrontFromCache() {
        val provider = RecordingProvider(cached = null)
        val interceptor = provider.interceptor()
        val otherSha = "b1674191a88ec5cdd733e4240a81803105dc412d6c6708d53ab94fc248f4f553"

        // First blob learns the host from the 401 and asks for a token.
        val first = fakeChain("https://$host/media/$sha.png", codes = listOf(401))
        interceptor.intercept(first.asChain()).close()
        assertEquals(1, first.requests.size)

        // Once that token has landed, the next blob on the same host is signed
        // on its first attempt — no anonymous probe.
        provider.cached = "Nostr token"
        val second = fakeChain("https://$host/media/$otherSha.png", codes = listOf(200))
        val response = interceptor.intercept(second.asChain())

        assertEquals(200, response.code)
        assertEquals("no anonymous probe on a known-gated host", 1, second.requests.size)
        assertEquals("Nostr token", second.requests.single().header("Authorization"))
        response.close()
    }

    @Test
    fun learnedHostWithoutTokenDoesNotLoop() {
        val provider = RecordingProvider(cached = null)
        val interceptor = provider.interceptor()

        val first = fakeChain("https://$host/media/$sha.png", codes = listOf(401))
        interceptor.intercept(first.asChain()).close()

        // Host is known but no token was minted (no signer). The preemptive path
        // must fall back to a single anonymous request rather than looping.
        val second = fakeChain("https://$host/media/$sha.png", codes = listOf(401))
        val response = interceptor.intercept(second.asChain())

        assertEquals(401, response.code)
        assertEquals(1, second.requests.size)
        response.close()
    }

    /**
     * The point of the whole split: `intercept` runs on an OkHttp dispatcher
     * thread and holds one of the 16 per-host slots for as long as it stays
     * there, so it must return without waiting for a signature.
     *
     * Uses the real provider and a deliberately slow signer rather than a fake
     * `warm`: what is being measured is that the production wiring hands the
     * signing off, not that a stub returns quickly.
     */
    @Test
    fun interceptDoesNotWaitForTheSignature() {
        val slowSigner = DelayingTestSigner(delayMs = SIGN_MS)
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val provider = BlossomReadAuthTokenProvider({ slowSigner }, scope)
            val interceptor = BlossomReadAuthInterceptor(provider::cachedHeader, provider::warm)
            val chain = fakeChain("https://$host/media/$sha.png", codes = listOf(401))

            // What the previous design cost: the interceptor bridged the suspend
            // signer with runBlocking, so the calling thread wore the full
            // signing latency. Same signer, same host, measured on this thread.
            val blockingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val blockingProbe = BlossomReadAuthTokenProvider({ DelayingTestSigner(delayMs = SIGN_MS) }, blockingScope)
            val beforeAt = System.nanoTime()
            runBlocking { blockingProbe.header(host) }
            val beforeMs = (System.nanoTime() - beforeAt) / 1_000_000
            blockingScope.cancel()

            val startedAt = System.nanoTime()
            interceptor.intercept(chain.asChain()).close()
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            println(
                "[measure] signature=${SIGN_MS}ms  waiting-for-it=${beforeMs}ms  " +
                    "intercept-now=${elapsedMs}ms",
            )

            assertTrue(
                "intercept must not wait out the ${SIGN_MS}ms signature; it took ${elapsedMs}ms",
                elapsedMs < SIGN_MS / 2,
            )
            assertNull(
                "returning before the token exists is exactly the point",
                provider.cachedHeader(host),
            )

            // ...and the signature it kicked off does still complete.
            runBlocking {
                withTimeout(SIGN_MS * 20) {
                    while (provider.cachedHeader(host) == null) delay(10)
                }
            }
            assertEquals(1, slowSigner.signatures)
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        // Long enough that a blocking implementation could not possibly pass the
        // assertion above, short enough to keep the suite quick.
        const val SIGN_MS = 2_000L
    }

    private class RecordingProvider(
        var cached: String?,
    ) {
        val warmed = mutableListOf<String>()

        fun cachedHeader(host: String): String? = cached

        fun warm(host: String) {
            warmed.add(host)
        }

        fun interceptor() = BlossomReadAuthInterceptor(::cachedHeader, ::warm)
    }

    /**
     * Records every [Request] it is asked to proceed and answers each with the
     * next code from [codes], so `[401, 200]` models "anonymous fails,
     * authenticated succeeds".
     */
    private class FakeChain(
        private val request: Request,
        private val codes: List<Int>,
    ) {
        val requests = mutableListOf<Request>()

        fun asChain(): Interceptor.Chain =
            Proxy.newProxyInstance(
                Interceptor.Chain::class.java.classLoader,
                arrayOf(Interceptor.Chain::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "request" -> request
                    "proceed" -> {
                        val proceeded = args[0] as Request
                        requests.add(proceeded)
                        Response
                            .Builder()
                            .request(proceeded)
                            .protocol(Protocol.HTTP_1_1)
                            .code(codes[requests.size - 1])
                            .message("msg")
                            .body("".toResponseBody(null))
                            .build()
                    }
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as Interceptor.Chain
    }

    private fun fakeChain(
        url: String,
        codes: List<Int>,
        method: String = "GET",
        preAuthHeader: String? = null,
    ): FakeChain {
        val request =
            Request
                .Builder()
                .url(url.toHttpUrl())
                .apply {
                    if (method == "GET") get() else method(method, "".toRequestBody())
                    preAuthHeader?.let { header("Authorization", it) }
                }.build()
        return FakeChain(request, codes)
    }
}
