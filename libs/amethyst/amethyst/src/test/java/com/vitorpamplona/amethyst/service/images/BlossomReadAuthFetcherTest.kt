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
package com.vitorpamplona.amethyst.service.images

import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.network.HttpException
import coil3.network.NetworkResponse
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthTokenProvider
import com.vitorpamplona.amethyst.commons.service.image.BlossomReadAuthFetcher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The signed retry that used to live in `BlossomReadAuthInterceptor` (behind a
 * `runBlocking`) now lives here, where `fetch()` is already `suspend`.
 */
class BlossomReadAuthFetcherTest {
    private val sha = "2c5287a55cc550c9d6bc4206a4663900e083315f4a544ea3bc189e43dc330af6"
    private val host = "nosfabrica.communities.buzz.xyz"
    private val url = "https://nosfabrica.communities.buzz.xyz/media/2c5287a55cc550c9d6bc4206a4663900e083315f4a544ea3bc189e43dc330af6.png"
    private val signer = NostrSignerInternal(KeyPair())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    private fun unauthorized() = HttpException(NetworkResponse(code = 401))

    private fun notFound() = HttpException(NetworkResponse(code = 404))

    /** Records the header each attempt carried, and fails the attempts in [failWith]. */
    private class RecordingBuilder(
        private val failWith: List<HttpException?>,
    ) {
        val headers = mutableListOf<String?>()

        fun build(authHeader: String?): Fetcher {
            val attempt = headers.size
            headers.add(authHeader)
            return Fetcher {
                failWith.getOrNull(attempt)?.let { throw it }
                null
            }
        }
    }

    private fun Fetcher(block: suspend () -> FetchResult?): Fetcher =
        object : Fetcher {
            override suspend fun fetch(): FetchResult? = block()
        }

    @Test
    fun anonymousSuccessNeverSigns() =
        runBlocking {
            val builder = RecordingBuilder(failWith = listOf(null))
            val provider = BlossomReadAuthTokenProvider({ signer }, scope)

            BlossomReadAuthFetcher(url, provider, builder::build).fetch()

            assertEquals("one attempt only", 1, builder.headers.size)
            assertNull("and it must be anonymous", builder.headers.single())
            assertNull("no token minted for a host that never 401'd", provider.cachedHeader(host))
        }

    @Test
    fun signsAndRetriesOn401() =
        runBlocking {
            val builder = RecordingBuilder(failWith = listOf(unauthorized(), null))
            val provider = BlossomReadAuthTokenProvider({ signer }, scope)

            BlossomReadAuthFetcher(url, provider, builder::build).fetch()

            assertEquals("anonymous attempt then signed retry", 2, builder.headers.size)
            assertNull(builder.headers[0])
            assertTrue(
                "retry must carry a Nostr token, got ${builder.headers[1]}",
                builder.headers[1]!!.startsWith("Nostr "),
            )
        }

    @Test
    fun nonAuthFailuresPropagateUnretried() =
        runBlocking {
            val builder = RecordingBuilder(failWith = listOf(notFound()))
            val provider = BlossomReadAuthTokenProvider({ signer }, scope)

            assertThrows(HttpException::class.java) {
                runBlocking { BlossomReadAuthFetcher(url, provider, builder::build).fetch() }
            }
            assertEquals("a 404 must not be retried", 1, builder.headers.size)
        }

    @Test
    fun without401CapableSignerThe401Propagates() =
        runBlocking {
            val builder = RecordingBuilder(failWith = listOf(unauthorized()))
            val provider = BlossomReadAuthTokenProvider({ null }, scope)

            assertThrows(HttpException::class.java) {
                runBlocking { BlossomReadAuthFetcher(url, provider, builder::build).fetch() }
            }
            assertEquals("no signer means no retry", 1, builder.headers.size)
        }

    @Test
    fun nonBlossomUrlIsNotRetried() =
        runBlocking {
            val builder = RecordingBuilder(failWith = listOf(unauthorized()))
            val provider = BlossomReadAuthTokenProvider({ signer }, scope)

            assertThrows(HttpException::class.java) {
                runBlocking {
                    BlossomReadAuthFetcher("https://example.com/media/avatar.png", provider, builder::build).fetch()
                }
            }
            assertEquals(1, builder.headers.size)
        }

    /**
     * The feed shape: a burst of images from a gated host all 401 at once. Each
     * retries, but they share one signature via the provider's single-flight.
     */
    @Test
    fun aBurstOf401sSharesOneSignature() =
        runBlocking {
            val provider = BlossomReadAuthTokenProvider({ signer }, scope)
            val builders = (1..16).map { RecordingBuilder(failWith = listOf(unauthorized(), null)) }

            builders
                .map { b ->
                    async(Dispatchers.Default) {
                        BlossomReadAuthFetcher(url, provider, b::build).fetch()
                    }
                }.awaitAll()

            val retryHeaders = builders.map { it.headers[1] }
            assertTrue("every retry must be signed", retryHeaders.all { it != null })
            assertEquals("all 16 retries must reuse one token", 1, retryHeaders.toSet().size)
        }
}
