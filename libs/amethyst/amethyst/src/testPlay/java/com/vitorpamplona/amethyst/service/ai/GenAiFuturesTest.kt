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
package com.vitorpamplona.amethyst.service.ai

import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * The ML Kit GenAI futures must never be cancelled: `genai-rewriting` 1.0.0-beta1 registers a
 * possibly-null `ICancellationCallback` as the cancellation listener, so a cancel dereferences
 * null on its own worker thread and kills the process. See [awaitDetached].
 */
class GenAiFuturesTest {
    /** Minimal hand-rolled future: records cancellation and completes on demand. */
    private class FakeFuture<T> : ListenableFuture<T> {
        private val listeners = mutableListOf<Pair<Runnable, Executor>>()
        private var value: T? = null
        private var failure: Throwable? = null
        private var done = false

        var cancelCalls = 0
            private set

        fun complete(result: T) = finish { value = result }

        fun fail(error: Throwable) = finish { failure = error }

        private fun finish(set: () -> Unit) {
            if (done) return
            set()
            done = true
            listeners.forEach { (runnable, executor) -> executor.execute(runnable) }
            listeners.clear()
        }

        override fun addListener(
            listener: Runnable,
            executor: Executor,
        ) {
            if (done) executor.execute(listener) else listeners.add(listener to executor)
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            cancelCalls++
            finish { failure = CancellationException("cancelled") }
            return true
        }

        override fun isCancelled() = failure is CancellationException

        override fun isDone() = done

        override fun get(): T {
            check(done) { "not done" }
            failure?.let { if (it is CancellationException) throw it else throw ExecutionException(it) }
            @Suppress("UNCHECKED_CAST")
            return value as T
        }

        override fun get(
            timeout: Long,
            unit: TimeUnit,
        ): T = get()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `returns the value the future completes with`() =
        runTest {
            val future = FakeFuture<String>()
            val awaited = async { future.awaitDetached() }
            advanceUntilIdle()

            future.complete("rewritten")

            assertEquals("rewritten", awaited.await())
        }

    @Test
    fun `unwraps the cause out of an ExecutionException`() =
        runTest {
            val future = FakeFuture<String>()
            future.fail(IllegalStateException("AICore is not available"))

            try {
                future.awaitDetached()
                fail("Expected the cause to surface")
            } catch (e: IllegalStateException) {
                assertEquals("AICore is not available", e.message)
            }
        }

    /** The regression this file exists for. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cancelling the caller does not cancel the future`() =
        runTest {
            val future = FakeFuture<String>()
            val job = launch { future.awaitDetached() }
            advanceUntilIdle()

            job.cancel()
            advanceUntilIdle()

            assertEquals(0, future.cancelCalls)
            assertFalse(future.isDone)

            // And the inference still finishing afterwards must not blow up on the detached caller.
            future.complete("rewritten")
            assertTrue(job.isCancelled)
        }
}
