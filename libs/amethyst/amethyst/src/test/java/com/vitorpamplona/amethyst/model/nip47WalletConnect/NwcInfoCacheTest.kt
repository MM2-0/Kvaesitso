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
package com.vitorpamplona.amethyst.model.nip47WalletConnect

import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcInfoCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NwcInfoCacheTest {
    private var clock = 1_000L

    /**
     * Unconfined so a caller runs inline up to its first real suspension, but [NwcInfoCache] still
     * hands its fetches to [Dispatchers.IO] — so this scope owns real background work and must not
     * outlive the test. A fetch still running afterwards has no
     * [kotlinx.coroutines.CoroutineExceptionHandler] in context, and anything it throws lands on
     * the JVM-wide handler, where `runTest` reports it against whichever unrelated test class
     * starts next.
     */
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    private fun uri(pubkey: String) = Nip47WalletConnect.Nip47URINorm(pubkey, relay, "secret")

    private fun info(content: String) = NwcInfoEvent("id", "pub", 0L, arrayOf(arrayOf("encryption", "nip44_v2")), content, "sig")

    @Test
    fun cachesWithinTtl() =
        runBlocking {
            var calls = 0
            val cache =
                NwcInfoCache(
                    fetch = {
                        calls++
                        info("pay_invoice notifications")
                    },
                    scope = scope,
                    ttlSeconds = 100,
                    now = { clock },
                )

            val a = cache.getFresh(uri("wallet1"))
            val b = cache.getFresh(uri("wallet1"))

            assertEquals(1, calls)
            assertNotNull(a)
            assertNotNull(b)
        }

    @Test
    fun refetchesAfterTtlExpires() =
        runBlocking {
            var calls = 0
            val cache =
                NwcInfoCache(
                    fetch = {
                        calls++
                        info("pay_invoice")
                    },
                    scope = scope,
                    ttlSeconds = 100,
                    now = { clock },
                )

            cache.getFresh(uri("wallet1"))
            clock += 101 // advance past the TTL window
            cache.getFresh(uri("wallet1"))

            assertEquals(2, calls)
        }

    @Test
    fun doesNotCacheFailures() =
        runBlocking {
            var calls = 0
            val cache =
                NwcInfoCache(
                    fetch = {
                        calls++
                        if (calls == 1) throw RuntimeException("boom") else info("pay_invoice")
                    },
                    scope = scope,
                    ttlSeconds = 100,
                    now = { clock },
                )

            // First fetch throws -> returns the (absent) prior value and is NOT cached.
            assertNull(cache.getFresh(uri("wallet1")))
            // Second call retries instead of being pinned to the failure.
            assertNotNull(cache.getFresh(uri("wallet1")))
            assertEquals(2, calls)
        }

    @Test
    fun cachesDefinitiveMissingInfo() =
        runBlocking {
            var calls = 0
            val cache =
                NwcInfoCache(
                    fetch = {
                        calls++
                        null // wallet published no info event
                    },
                    scope = scope,
                    ttlSeconds = 100,
                    now = { clock },
                )

            assertNull(cache.getFresh(uri("wallet1")))
            assertNull(cache.getFresh(uri("wallet1")))
            assertEquals(1, calls) // a definitive "no info" is cached within the TTL
        }

    @Test
    fun currentIsNonBlockingAndReflectsCache() =
        runBlocking {
            val cache =
                NwcInfoCache(
                    fetch = { info("pay_invoice") },
                    scope = scope,
                    ttlSeconds = 100,
                    now = { clock },
                )

            assertNull(cache.current(uri("wallet1"))) // nothing fetched yet
            cache.getFresh(uri("wallet1"))
            assertNotNull(cache.current(uri("wallet1")))
        }

    // --- one fetch per wallet, however many callers ask at once ---

    /**
     * A fetch that reports when it has started and then blocks until released, so
     * a test can put callers into a known interleaving without sleeping: caller
     * one is provably inside the fetch before the others arrive.
     */
    private class GatedFetch(
        private val result: NwcInfoEvent?,
    ) {
        val calls = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        suspend fun fetch(uri: Nip47WalletConnect.Nip47URINorm): NwcInfoEvent? {
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
            return result
        }
    }

    @Test
    fun concurrentGetFreshCallersShareOneFetch() =
        runBlocking {
            val fetcher = GatedFetch(info("pay_invoice"))
            val cache = NwcInfoCache(fetch = fetcher::fetch, scope = scope, ttlSeconds = 100, now = { clock })

            val results =
                coroutineScope {
                    // Unconfined, not IO: an unconfined coroutine runs its body inline until the
                    // first real suspension, so by the time `async` returns, the caller has already
                    // executed `inFlight.putIfAbsent` and parked on the winner's deferred. On IO it
                    // had only been *scheduled*, and the gate below could open before it arrived --
                    // the winner then removed the in-flight entry, the late caller found none, and
                    // started a second fetch. That is the whole race, and it made this test fail
                    // about one run in four under full-suite load while passing alone every time.
                    val first = async(Dispatchers.Unconfined) { cache.getFresh(uri("wallet1")) }
                    fetcher.started.await()
                    val rest = (1..4).map { async(Dispatchers.Unconfined) { cache.getFresh(uri("wallet1")) } }
                    fetcher.release.complete(Unit)
                    (listOf(first) + rest).awaitAll()
                }

            assertEquals(1, fetcher.calls.get())
            assertTrue(results.all { it != null })
        }

    @Test
    fun getFreshJoinsFetchAlreadyStartedByRefreshIfStale() =
        runBlocking {
            val fetcher = GatedFetch(info("pay_invoice"))
            val cache = NwcInfoCache(fetch = fetcher::fetch, scope = scope, ttlSeconds = 100, now = { clock })

            cache.refreshIfStale(uri("wallet1"))
            fetcher.started.await()

            val joined =
                coroutineScope {
                    // Unconfined for the same reason as the concurrent-callers test above: the
                    // joiner has to reach `inFlight.putIfAbsent` before the gate opens, and on IO
                    // it is merely scheduled. Same latent race, not yet observed failing.
                    val caller = async(Dispatchers.Unconfined) { cache.getFresh(uri("wallet1")) }
                    fetcher.release.complete(Unit)
                    caller.await()
                }

            assertEquals(1, fetcher.calls.get())
            assertNotNull(joined)
        }

    // --- currentOrFetch: wait only when there is nothing cached at all ---

    @Test
    fun currentOrFetchWaitsWhenNothingIsCached() =
        runBlocking {
            val fetcher = GatedFetch(info("pay_invoice"))
            val cache = NwcInfoCache(fetch = fetcher::fetch, scope = scope, ttlSeconds = 100, now = { clock })

            val result =
                coroutineScope {
                    val caller = async(Dispatchers.IO) { cache.currentOrFetch(uri("wallet1")) }
                    fetcher.started.await()
                    fetcher.release.complete(Unit)
                    caller.await()
                }

            assertEquals(1, fetcher.calls.get())
            assertNotNull("a cold cache must resolve before the caller decides on encryption", result)
        }

    @Test
    fun currentOrFetchReturnsStaleEntryWithoutWaiting() =
        runBlocking {
            // The background refresh is made to hang, so waiting on it would hang the
            // test. Returning at all is the assertion.
            val hang = CompletableDeferred<Unit>()
            val calls = AtomicInteger(0)
            val cache =
                NwcInfoCache(
                    fetch = {
                        if (calls.incrementAndGet() > 1) hang.await()
                        info("pay_invoice")
                    },
                    scope = scope,
                    ttlSeconds = 100,
                    now = { clock },
                )

            assertNotNull(cache.currentOrFetch(uri("wallet1")))
            assertEquals(1, calls.get())

            // Past the TTL the entry is stale, but it still says which encryption the
            // wallet advertises, so it comes back as-is while the refresh runs on.
            clock += 1_000
            val stale = cache.currentOrFetch(uri("wallet1"))
            hang.complete(Unit)
            assertNotNull(stale)
        }

    @Test
    fun cancellingTheCallerDoesNotAbortTheSharedFetch() =
        runBlocking {
            // A payment coroutine that asks first must not own the fetch: backing out
            // of the payment screen would cancel it, leaving the cache cold so the
            // next attempt pays the whole cost again.
            val fetcher = GatedFetch(info("pay_invoice"))
            val cache = NwcInfoCache(fetch = fetcher::fetch, scope = scope, ttlSeconds = 100, now = { clock })

            val caller = launch(Dispatchers.IO) { cache.currentOrFetch(uri("wallet1")) }
            fetcher.started.await()
            caller.cancelAndJoin()
            fetcher.release.complete(Unit)

            withTimeout(5_000) {
                while (cache.current(uri("wallet1")) == null) delay(5)
            }
            assertEquals("the abandoned fetch still completed and warmed the cache", 1, fetcher.calls.get())
        }

    @Test
    fun aDeadScopeReleasesCallersInsteadOfHanging() =
        runBlocking {
            // Account.scope is cancelled on logout (AccountCacheState.removeAccount),
            // which runs asynchronously and can race a payment already in flight.
            // Launching into it does not run the body, so nothing would complete the
            // deferred a caller is awaiting.
            val dead = CoroutineScope(Dispatchers.IO).also { it.cancel() }
            val fetcher = GatedFetch(info("pay_invoice"))
            val cache = NwcInfoCache(fetch = fetcher::fetch, scope = dead, ttlSeconds = 100, now = { clock })

            assertNull(withTimeout(2_000) { cache.currentOrFetch(uri("wallet1")) })
            assertEquals(0, fetcher.calls.get())

            // and the abandoned slot must not poison every later call for that wallet
            assertNull(withTimeout(2_000) { cache.currentOrFetch(uri("wallet1")) })
        }
}
