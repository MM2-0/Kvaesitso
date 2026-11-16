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

import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthTokenProvider
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.encoding.Base64

class BlossomReadAuthTokenProviderTest {
    private val sha = "2c5287a55cc550c9d6bc4206a4663900e083315f4a544ea3bc189e43dc330af6"
    private val host = "nosfabrica.communities.buzz.xyz"
    private val signer = NostrSignerInternal(KeyPair())

    // A real dispatcher, not runTest's virtual clock: the concurrency test below
    // measures wall time, which virtual time would collapse to zero.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun signsAndFormatsHeader() =
        runBlocking {
            val provider = BlossomReadAuthTokenProvider({ signer }, scope)
            val header = provider.header(host)
            assertTrue("expected a Nostr auth header, got $header", header!!.startsWith("Nostr "))
        }

    @Test
    fun returnsNullWhenNoSigner() =
        runBlocking {
            val provider = BlossomReadAuthTokenProvider({ null }, scope)
            assertNull(provider.header(host))
        }

    @Test
    fun cachedHeaderNeverSigns() =
        runBlocking {
            var lookups = 0
            val provider =
                BlossomReadAuthTokenProvider({
                    lookups++
                    signer
                }, scope)

            // Nothing minted yet, so the pure read must miss without signing.
            assertNull(provider.cachedHeader(host))
            assertEquals(0, lookups)

            val minted = provider.header(host)
            assertEquals(minted, provider.cachedHeader(host))
        }

    @Test
    fun cachesPerHostWithinTtl() =
        runBlocking {
            var lookups = 0
            val provider =
                BlossomReadAuthTokenProvider({
                    lookups++
                    signer
                }, scope, clock = { 0L })

            val first = provider.header(host)
            val second = provider.header(host)

            assertEquals("second call must be served from cache", first, second)
            assertEquals("signer must be resolved only once for the same host", 1, lookups)
        }

    @Test
    fun differentHostSignsSeparately() =
        runBlocking {
            val provider = BlossomReadAuthTokenProvider({ signer }, scope, clock = { 0L })
            val a = provider.header(host)
            val b = provider.header("other.example.com")
            assertNotEquals(a, b)
        }

    @Test
    fun refreshesAfterExpiry() =
        runBlocking {
            var now = 0L
            var lookups = 0
            val provider =
                BlossomReadAuthTokenProvider({
                    lookups++
                    signer
                }, scope, clock = { now })

            assertNotNull(provider.header(host))
            assertEquals("the first call must sign", 1, lookups)

            now += 56L * 60L * 1000L
            assertNull("token must be gone from the pure read once expired", provider.cachedHeader(host))

            // Counts signatures rather than comparing the two headers: the injected [clock] only
            // drives the cache TTL, while the signed BlossomAuthorizationEvent takes its
            // `created_at` from the real wall clock (TimeUtils.now()). Both signings land in the
            // same second on any quick machine, so the two events — and therefore their ids, sigs
            // and headers — are byte-identical, and an assertNotEquals on them fails even though
            // the token was correctly re-signed. The signer is only ever consulted on a real
            // signing pass (a cache hit returns before it), so this counter says exactly that.
            assertNotNull(provider.header(host))
            assertEquals("an expired token must be re-signed", 2, lookups)
        }

    @Test
    fun warmPopulatesTheCacheWithoutTheCallerWaiting() =
        runBlocking {
            val provider = BlossomReadAuthTokenProvider({ signer }, scope)

            provider.warm(host)

            // warm() returns immediately; the token lands shortly after.
            withTimeout(5_000) {
                while (provider.cachedHeader(host) == null) {
                    kotlinx.coroutines.delay(5)
                }
            }
            assertTrue(provider.cachedHeader(host)!!.startsWith("Nostr "))
        }

    /**
     * End-to-end BUD-11 check on the token this path actually mints: reused
     * across every blob on the host, so it must be `server`-scoped and carry no
     * `x` tag ("When `x` tags are present, the token is only valid for
     * operations on the specified blob hashes"), and be standard padded Base64
     * that a strict decoder accepts.
     */
    @Test
    fun mintedTokenIsAReusableBud11GetToken() =
        runBlocking {
            val provider = BlossomReadAuthTokenProvider({ signer }, scope)

            val token =
                provider.header(host)!!.removePrefix(BlossomAuthorizationEvent.AUTH_HEADER_SCHEME)

            // Strict standard decode, as deployed servers do — see BlossomAuthorizationEvent.rawToken.
            val event = Base64.decode(token).decodeToString()
            val parsed = Event.fromJson(event) as BlossomAuthorizationEvent

            assertEquals(BlossomAuthorizationEvent.KIND, parsed.kind)
            assertEquals("get", parsed.tags.first { it[0] == "t" }[1])
            assertEquals(host, parsed.tags.first { it[0] == "server" }[1])
            assertTrue("a host-cached token must not be blob-scoped", parsed.tags.none { it[0] == "x" })
            assertTrue(
                "BUD-11 requires an expiration in the future",
                parsed.tags.first { it[0] == "expiration" }[1].toLong() > parsed.createdAt,
            )
        }

    /**
     * The single-flight guarantee. Before it existed the token cache was only
     * populated *after* a signature returned, so a cold burst of N images from a
     * gated host all missed and all signed — N signatures, and with a NIP-55
     * external signer N IPC round trips.
     */
    @Test
    fun concurrentCallersShareOneSignature() =
        runBlocking {
            val slow = DelayingTestSigner(delayMs = SIGN_MS)
            val provider = BlossomReadAuthTokenProvider({ slow }, scope)

            val startedAt = System.nanoTime()
            val results =
                (1..CONCURRENT_CALLERS)
                    .map { async(Dispatchers.Default) { provider.header(host) } }
                    .awaitAll()
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            println(
                "[measure] callers=$CONCURRENT_CALLERS  signatures=${slow.signatures}  " +
                    "one-signature=${SIGN_MS}ms  all-callers-done=${elapsedMs}ms",
            )

            assertEquals("exactly one signature for $CONCURRENT_CALLERS callers", 1, slow.signatures)
            assertEquals("every caller must get the same token", 1, results.toSet().size)
            assertTrue("first token must be non-null", results.first() != null)
            assertTrue(
                "$CONCURRENT_CALLERS callers should share one ~${SIGN_MS}ms signature, took ${elapsedMs}ms",
                elapsedMs < SIGN_MS * 3,
            )
        }

    /**
     * The same single-flight guarantee, but with a signature that returns almost
     * immediately — an in-process [NostrSignerInternal], which is what the image
     * path uses for a local key.
     *
     * A fast signature is the harder case: the leader can finish, cache its token
     * and retire its in-flight entry while a straggler is still between its own
     * cache miss and its look at the in-flight map. That straggler finds both
     * empty, and must pick the just-minted token up instead of signing a second
     * one. Run over many rounds because the window is only microseconds wide.
     */
    @Test
    fun aFastSignerStillSharesOneSignature() =
        runBlocking {
            repeat(ROUNDS) { round ->
                val instant = DelayingTestSigner(delayMs = 0)
                val provider = BlossomReadAuthTokenProvider({ instant }, scope)

                val results =
                    (1..CONCURRENT_CALLERS)
                        .map { async(Dispatchers.Default) { provider.header(host) } }
                        .awaitAll()

                assertEquals("round $round: one signature for $CONCURRENT_CALLERS callers", 1, instant.signatures)
                assertEquals("round $round: every caller must get the same token", 1, results.toSet().size)
                assertNotNull("round $round: token must be non-null", results.first())
            }
        }

    private companion object {
        // Matches OkHttpClientFactory's maxRequestsPerHost: the worst realistic
        // burst is one gated host filling every per-host dispatcher slot.
        const val CONCURRENT_CALLERS = 16

        // How long one signature takes in the concurrency test.
        const val SIGN_MS = 300L

        // Rounds of the fast-signer burst. The leader-finished-early window is
        // microseconds wide, so one round hits it only now and then.
        const val ROUNDS = 200
    }
}
