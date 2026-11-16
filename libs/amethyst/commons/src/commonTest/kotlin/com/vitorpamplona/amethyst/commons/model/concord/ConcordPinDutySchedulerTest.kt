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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.actions.ConcordChannelPins
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The account-level pin duty scheduler (CORD-04 §7): a duty waits its random delay, a channel runs
 * one duty at a time, and a debt is attempted once — so a burst of ticks costs one write and a failing
 * write never spins — while a new debt is a new attempt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConcordPinDutySchedulerTest {
    @Test
    fun oneAttemptPerDebtAndOneDutyPerChannel() =
        runTest {
            val scheduler = ConcordPinDutyScheduler(delayMs = { 5_000L })
            var runs = 0

            assertTrue(scheduler.schedule(this, "c|a", "d1") { runs++ })
            assertFalse(scheduler.schedule(this, "c|a", "d1") { runs++ }, "the same debt twice")
            assertFalse(scheduler.schedule(this, "c|a", "d2") { runs++ }, "a second duty while one is in flight")
            assertTrue(scheduler.schedule(this, "c|b", "d1") { runs++ }, "another channel is independent")

            advanceTimeBy(4_999)
            runCurrent()
            assertEquals(0, runs, "a duty waits its delay before settling")
            advanceUntilIdle()
            assertEquals(2, runs)

            assertFalse(scheduler.schedule(this, "c|a", "d1") { runs++ }, "an attempted debt is never respun")
            assertTrue(scheduler.schedule(this, "c|a", "d2") { runs++ }, "a new debt is a new attempt")
            advanceUntilIdle()
            assertEquals(3, runs)
        }

    @Test
    fun nothingOwedSchedulesNothing() =
        runTest {
            val scheduler = ConcordPinDutyScheduler(delayMs = { 0L })
            assertFalse(scheduler.schedule(this, "c|a", null) { error("must not run") })
            assertNull(ConcordPinDutyScheduler.debtOf(null))
            assertNull(ConcordPinDutyScheduler.debtOf(ConcordChannelPins.none("aa".repeat(32))))
        }
}
