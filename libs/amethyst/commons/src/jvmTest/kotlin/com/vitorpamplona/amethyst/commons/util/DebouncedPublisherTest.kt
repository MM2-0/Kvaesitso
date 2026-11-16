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
package com.vitorpamplona.amethyst.commons.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The publish side of the navigation pickers: a configuring session must reach the relays as one
 * event, and no exit path may strand the last toggle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedPublisherTest {
    private val debounce = 2_000L

    @Test
    fun aBurstOfEditsPublishesOnce() =
        runTest {
            var published = 0
            val publisher = publisher { published++ }

            repeat(10) {
                publisher.schedule()
                advanceTimeBy(200)
            }
            advanceUntilIdle()

            assertEquals(1, published)
        }

    @Test
    fun editsFurtherApartThanTheDebouncePublishSeparately() =
        runTest {
            var published = 0
            val publisher = publisher { published++ }

            publisher.schedule()
            advanceUntilIdle()
            publisher.schedule()
            advanceUntilIdle()

            assertEquals(2, published)
        }

    @Test
    fun flushPublishesWithoutWaitingOutTheDebounce() =
        runTest {
            var published = 0
            val publisher = publisher { published++ }

            publisher.schedule()
            advanceTimeBy(100)
            assertEquals(0, published)

            publisher.flush()
            advanceUntilIdle()

            assertEquals(1, published)
        }

    @Test
    fun flushingTwiceOnTheWayOutDoesNotPublishTwice() =
        runTest {
            // Leaving a picker screen can hit both arms of FlushPickerEditsOnExit (ON_STOP, then
            // onDispose), and onCleared may follow. Only the first one may publish.
            var published = 0
            val publisher = publisher { published++ }

            publisher.schedule()
            publisher.flush()
            advanceUntilIdle()

            publisher.flush()
            advanceUntilIdle()

            assertEquals(1, published)
        }

    @Test
    fun flushDuringAnInFlightPublishDoesNotRestartIt() =
        runTest {
            // A publish can sit for seconds waiting on a remote signer. Leaving the screen then
            // flushes; that must not cancel the signing request and send a second one.
            var started = 0
            var finished = 0
            val publisher =
                publisher {
                    started++
                    delay(5_000)
                    finished++
                }

            publisher.schedule()
            advanceTimeBy(debounce + 100)
            assertEquals(1, started)

            publisher.flush()
            advanceUntilIdle()

            assertEquals(1, started)
            assertEquals(1, finished)
        }

    @Test
    fun anEditDuringAnInFlightPublishQueuesAnotherInsteadOfCancellingIt() =
        runTest {
            // The reaction choices screen saves on every tap, so a tap can land while the previous
            // publish waits on a remote signer. Cancelling it would drop that signing request and
            // start over; it must finish, and the new edit publish after its own debounce.
            var started = 0
            var finished = 0
            val publisher =
                publisher {
                    started++
                    delay(5_000)
                    finished++
                }

            publisher.schedule()
            advanceTimeBy(debounce + 100)
            assertEquals(1, started)

            publisher.schedule()
            advanceUntilIdle()

            assertEquals(2, started)
            assertEquals(2, finished)
        }

    @Test
    fun flushWithNothingPendingIsANoOp() =
        runTest {
            var published = 0
            val publisher = publisher { published++ }

            publisher.flush()
            advanceUntilIdle()

            assertEquals(0, published)
        }

    @Test
    fun aPendingPublishDiesWithTheScopeItWasLaunchedOn() =
        runTest {
            // Why the pickers launch on the account's scope and not viewModelScope: a pending edit is
            // only as durable as the scope holding it, and AndroidX cancels viewModelScope BEFORE it
            // calls onCleared — so an edit tied to it is already gone by the time any teardown hook
            // there could rescue it.
            var published = 0
            val shortLived = CoroutineScope(coroutineContext + Job())
            val publisher = publisher(scope = shortLived) { published++ }

            publisher.schedule()
            shortLived.cancel()
            advanceUntilIdle()

            assertEquals(0, published)
        }

    private fun TestScope.publisher(
        scope: CoroutineScope = this,
        publish: suspend () -> Unit,
    ) = DebouncedPublisher(
        debounceMs = debounce,
        launch = { block -> scope.launch { block() } },
        publish = publish,
    )
}
