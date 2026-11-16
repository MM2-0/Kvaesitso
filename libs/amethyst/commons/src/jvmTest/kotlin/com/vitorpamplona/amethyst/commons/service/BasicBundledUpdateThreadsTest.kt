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
package com.vitorpamplona.amethyst.commons.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertTrue

/** Real threads racing the run's end: the last invalidation must always be followed by an update. */
class BasicBundledUpdateThreadsTest {
    @Test
    fun noInvalidationIsLostAcrossThreads() {
        repeat(10) { round ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val clock = AtomicLong(0)
            val lastInvalidate = AtomicLong(0)
            val lastUpdateStart = AtomicLong(0)
            val updater = BasicBundledUpdate(2, Dispatchers.IO, scope)
            val onUpdate: suspend () -> Unit = {
                lastUpdateStart.set(clock.incrementAndGet())
                delay(1)
            }
            (1..4)
                .map {
                    thread {
                        repeat(1000) {
                            lastInvalidate.accumulateAndGet(clock.incrementAndGet(), ::maxOf)
                            updater.invalidate(onUpdate = onUpdate)
                            if (it % 50 == 0) Thread.sleep(1)
                        }
                    }
                }.forEach { it.join() }
            Thread.sleep(300)
            scope.cancel()
            assertTrue(lastUpdateStart.get() > lastInvalidate.get(), "round $round: the last invalidation never got its update")
        }
    }
}
