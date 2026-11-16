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
package com.vitorpamplona.amethyst.commons.relayClient.preload

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MetadataRateLimiterTest {
    @Test
    fun partialBatchIsFlushedWithoutWaitingForTheChannelToClose() =
        runTest {
            val scope = TestScope(testScheduler)
            val requested = mutableListOf<String>()
            val limiter = MetadataRateLimiter(maxRequestsPerSecond = 20, scope = scope, flushDelayMs = 250)
            limiter.start { requested.add(it) }

            // Fewer pubkeys than one batch: the old collect-then-flush never ran.
            repeat(8) { limiter.enqueue("pubkey-$it") }
            scope.advanceTimeBy(1_000)

            assertEquals((0 until 8).map { "pubkey-$it" }, requested)
            scope.cancel()
        }

    @Test
    fun duplicatesAreRequestedOnce() =
        runTest {
            val scope = TestScope(testScheduler)
            val requested = mutableListOf<String>()
            val limiter = MetadataRateLimiter(maxRequestsPerSecond = 20, scope = scope, flushDelayMs = 250)
            limiter.start { requested.add(it) }

            limiter.enqueue("a")
            limiter.enqueue("a")
            scope.advanceTimeBy(2_000)
            limiter.enqueue("a")
            scope.advanceTimeBy(2_000)

            assertEquals(listOf("a"), requested)
            scope.cancel()
        }

    @Test
    fun fullBatchesStillRateLimit() =
        runTest {
            val scope = TestScope(testScheduler)
            val requested = mutableListOf<String>()
            val limiter = MetadataRateLimiter(maxRequestsPerSecond = 3, scope = scope, flushDelayMs = 250)
            limiter.start { requested.add(it) }

            repeat(5) { limiter.enqueue("p$it") }
            scope.advanceTimeBy(100)
            assertEquals(3, requested.size, "first full batch goes out immediately")
            scope.advanceTimeBy(2_000)
            assertEquals(5, requested.size, "the partial remainder is flushed after the pause")
            scope.cancel()
        }
}
