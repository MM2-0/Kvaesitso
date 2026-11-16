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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.AddressableAuthorRelayLoaderSubAssembler
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderQueryState
import com.vitorpamplona.quartz.nip01Core.core.Address
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class AddressableAuthorRelayLoaderSubAssemblerTest {
    /**
     * Unique per-test-class identities so the shared [LocalCache] singleton isn't polluted with
     * notes another test class also claims.
     */
    private fun stubKeys(count: Int): Set<EventFinderQueryState> {
        val account = mockk<Account>()
        return (1..count).mapTo(mutableSetOf()) { i ->
            val address = Address(30023, "ad04%060x".format(i), "d$i")
            EventFinderQueryState(LocalCache.getOrCreateAddressableNoteInternal(address), account)
        }
    }

    /**
     * Regression test for the `ConcurrentModificationException` in `SetsKt.minus` reported from
     * `DefaultDispatcher-worker-70`: this manager is genuinely re-entered from several threads at
     * once, because `ComposeSubscriptionManager.subscribe`/`unsubscribe` call `invalidateKeys()`
     * *after* releasing their own lock, and `LifecycleAwareSubscription`'s 30s grace-period
     * unsubscribe fires on a `Dispatchers.Default` worker.
     *
     * Overlap is detected through the injected `allKeys()` lambda rather than by catching — once
     * the body is bundled its throwables are swallowed by `BundledUpdate`'s
     * `CoroutineExceptionHandler` and would never reach the test thread.
     */
    @Test
    fun concurrentInvalidateFiltersNeverOverlap() {
        val userFinder = mockk<UserFinderFilterAssembler>(relaxed = true)
        val keys = stubKeys(50)

        val inFlight = AtomicInteger(0)
        val overlaps = AtomicInteger(0)
        val assembler =
            AddressableAuthorRelayLoaderSubAssembler(
                LocalCache,
                {
                    if (inFlight.incrementAndGet() > 1) overlaps.incrementAndGet()
                    try {
                        keys
                    } finally {
                        inFlight.decrementAndGet()
                    }
                },
                userFinder,
            )

        val start = CountDownLatch(1)
        try {
            val threads =
                (1..8).map {
                    thread(start = false) {
                        start.await()
                        repeat(500) { assembler.invalidateFilters() }
                    }
                }
            threads.forEach { it.start() }
            start.countDown()
            threads.forEach { it.join() }
        } finally {
            assembler.destroy()
        }

        assertEquals("forceInvalidate bodies overlapped", 0, overlaps.get())
    }

    /** The manager still does its job: unresolved stub authors reach the user finder. */
    @Test
    fun bridgesMissingAuthorsIntoUserFinder() {
        val userFinder = mockk<UserFinderFilterAssembler>(relaxed = true)
        val keys = stubKeys(3)
        val assembler = AddressableAuthorRelayLoaderSubAssembler(LocalCache, { keys }, userFinder)

        try {
            assembler.invalidateFilters()

            verify(timeout = 3000) {
                userFinder.subscribe(
                    match<List<UserFinderQueryState>> { it.size == 3 },
                )
            }
        } finally {
            assembler.destroy()
        }
    }

    /**
     * `destroy()` must win against a body that is already past its `allKeys()` scan: otherwise the
     * body re-subscribes authors `destroy()` just released, leaving live kind-0/10002 REQs (and
     * retained `User`/`Account` references) for a dead account after logout.
     *
     * The body is gated inside the injected `allKeys()` lambda so `destroy()` provably runs
     * underneath an in-flight run rather than racing it by luck.
     */
    @Test
    fun destroyDuringInFlightInvalidateDoesNotLeakSubscriptions() {
        val userFinder = mockk<UserFinderFilterAssembler>(relaxed = true)
        val subscribeHappened = CountDownLatch(1)
        every { userFinder.subscribe(any<List<UserFinderQueryState>>()) } answers {
            subscribeHappened.countDown()
        }

        val keys = stubKeys(3)
        val invalidateEntered = CountDownLatch(1)
        val destroyFinished = CountDownLatch(1)
        val assembler =
            AddressableAuthorRelayLoaderSubAssembler(
                LocalCache,
                {
                    invalidateEntered.countDown()
                    destroyFinished.await(5, TimeUnit.SECONDS)
                    keys
                },
                userFinder,
            )

        try {
            assembler.invalidateFilters()
            assertTrue("bundled run never started", invalidateEntered.await(5, TimeUnit.SECONDS))
        } finally {
            assembler.destroy()
            destroyFinished.countDown()
        }

        // The gated body resumes the instant destroyFinished counts down, so a leak shows up
        // immediately; the wait only has to outlast that hand-off.
        assertFalse(
            "in-flight body subscribed after destroy()",
            subscribeHappened.await(500, TimeUnit.MILLISECONDS),
        )
    }
}
