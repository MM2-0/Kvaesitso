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
package com.vitorpamplona.amethyst.commons.service.lnurl

import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointCache
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Deduplication itself belongs to `LnurlEndpointCache` and is pinned by
 * `LnurlEndpointCacheTest` in quartz. What is left to prove here is that this
 * resolver actually routes through it, so a zap-receipt burst turns into one
 * request on a real OkHttp client rather than one per receipt.
 */
class OkHttpLnurlEndpointResolverTest {
    /** Counts requests and holds each open long enough for the burst to pile up behind it. */
    private class CountingInterceptor : Interceptor {
        val calls = AtomicInteger(0)

        override fun intercept(chain: Interceptor.Chain): Response {
            calls.incrementAndGet()
            Thread.sleep(HOLD_MS)
            return Response
                .Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(BODY.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    /**
     * Releases [n] callers at once. The gate matters: the claim under test is
     * that these callers were all in flight together, and a straggler arriving
     * after the first response landed would legitimately issue a second request.
     */
    private suspend fun burst(n: Int): List<LnurlEndpointInfo?> =
        coroutineScope {
            val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
            val resolver = OkHttpLnurlEndpointResolver { client }
            val gate = CompletableDeferred<Unit>()
            val callers =
                (0 until n).map {
                    async(Dispatchers.IO) {
                        gate.await()
                        resolver.resolve(URL)
                    }
                }
            gate.complete(Unit)
            callers.awaitAll()
        }

    private val interceptor = CountingInterceptor()

    @BeforeTest
    fun clearCache() {
        LnurlEndpointCache.clear()
    }

    @Test
    fun `a burst of callers makes one http request`() =
        runBlocking {
            // The gate matters: the claim under test is that these callers were all
            // in flight together, and a straggler arriving after the first response
            // landed would legitimately issue a second request.
            val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
            val resolver = OkHttpLnurlEndpointResolver { client }
            val gate = CompletableDeferred<Unit>()
            val results =
                coroutineScope {
                    val callers =
                        (0 until 20).map {
                            async(Dispatchers.IO) {
                                gate.await()
                                resolver.resolve(URL)
                            }
                        }
                    gate.complete(Unit)
                    callers.awaitAll()
                }

            assertEquals(1, interceptor.calls.get(), "20 concurrent callers must share one request")
            assertNotNull(results.first(), "the shared fetch resolved")
            assertTrue(results.all { it == results.first() }, "every caller gets the same result")
        }

    private companion object {
        const val URL = "https://example.com/.well-known/lnurlp/vitor"
        const val BODY = """{"nostrPubkey":"aabbcc","allowsNostr":true}"""

        /** Long enough for the whole burst to pile up behind the first request. */
        const val HOLD_MS = 200L
    }
}
