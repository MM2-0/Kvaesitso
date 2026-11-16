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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BasicBundledUpdateTest {
    @Test
    fun aBurstRunsOnceThenOnceMoreAfterTheDelay() =
        runTest {
            val updater = BasicBundledUpdate(100, StandardTestDispatcher(testScheduler), this)
            var runs = 0
            val onUpdate: suspend () -> Unit = { runs++ }

            repeat(5) { updater.invalidate(onUpdate = onUpdate) }
            advanceUntilIdle()

            assertEquals(2, runs)
        }

    @Test
    fun anInvalidationDuringTheRepeatRunIsNotLost() =
        runTest {
            val updater = BasicBundledUpdate(100, StandardTestDispatcher(testScheduler), this)
            var runs = 0
            lateinit var onUpdate: suspend () -> Unit
            onUpdate = {
                runs++
                // The first run is followed by one invalidation; the repeat run gets another while it
                // is still working (an update that suspends, as the real ones do on I/O).
                if (runs <= 2) updater.invalidate(onUpdate = onUpdate)
                delay(10)
            }

            updater.invalidate(onUpdate = onUpdate)
            advanceUntilIdle()

            assertEquals(3, runs)
        }

    @Test
    fun ignoreIfDoingDoesNotQueueAnotherRun() =
        runTest {
            val updater = BasicBundledUpdate(100, StandardTestDispatcher(testScheduler), this)
            var runs = 0
            lateinit var onUpdate: suspend () -> Unit
            onUpdate = {
                runs++
                if (runs == 1) updater.invalidate(ignoreIfDoing = true, onUpdate = onUpdate)
                delay(10)
            }

            updater.invalidate(onUpdate = onUpdate)
            advanceUntilIdle()

            assertEquals(1, runs)
        }

    @Test
    fun aBurstOfCallsDuringARunStartsOnlyOneMore() =
        runTest {
            val updater = BasicBundledUpdate(100, StandardTestDispatcher(testScheduler), this)
            var runs = 0
            lateinit var onUpdate: suspend () -> Unit
            onUpdate = {
                runs++
                if (runs == 1) repeat(10_000) { updater.invalidate(onUpdate = onUpdate) }
                delay(10)
            }

            updater.invalidate(onUpdate = onUpdate)
            advanceUntilIdle()

            assertEquals(2, runs)
        }
}
