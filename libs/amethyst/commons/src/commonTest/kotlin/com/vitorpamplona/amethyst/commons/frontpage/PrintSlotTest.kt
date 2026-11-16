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
package com.vitorpamplona.amethyst.commons.frontpage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The press a print holds, and the Cancel that stops whichever print runs (M3 of the audit). */
class PrintSlotTest {
    @Test
    fun cancelStopsAScheduledPrintAndItsRunGoesOnToSaySo() =
        runTest {
            val slot = PrintSlot()
            val printing = CompletableDeferred<Unit>()
            var after: ScheduledOutcome? = null
            // A scheduled run: it holds the press from before its claim, prints, then records how it ended.
            val run =
                launch {
                    assertTrue(slot.tryHold())
                    try {
                        val ended =
                            slot.runCancellable(FrontPageRun.Cancelled) {
                                printing.complete(Unit)
                                awaitCancellation()
                            }
                        after = scheduledOutcomeOf(ended)
                    } finally {
                        slot.release()
                    }
                }
            printing.await()
            // A Print tap meanwhile finds the press taken: refused, not silently dropped.
            assertTrue(slot.isHeld)
            assertFalse(slot.tryHold())
            slot.cancel()
            run.join()
            assertEquals(ScheduledOutcome.CANCELLED, after)
            assertFalse(slot.isHeld)
        }

    @Test
    fun aRunTheCallerStopsIsCancelledAsUsual() =
        runTest {
            val slot = PrintSlot()
            val printing = CompletableDeferred<Unit>()
            var returned = false
            val run =
                launch {
                    slot.runCancellable(FrontPageRun.Cancelled) {
                        printing.complete(Unit)
                        awaitCancellation()
                    }
                    returned = true
                }
            printing.await()
            // The platform ends the scheduled run (its deadline): no "cancelled by the reader".
            run.cancel()
            run.join()
            assertTrue(run.isCancelled)
            assertFalse(returned)
        }

    @Test
    fun aPrintThatEndsReturnsItsResult() =
        runTest {
            val slot = PrintSlot()
            assertEquals(FrontPageRun.Idle, slot.runCancellable<FrontPageRun>(FrontPageRun.Cancelled) { FrontPageRun.Idle })
            // Nothing running: Cancel is a no-op.
            slot.cancel()
        }

    @Test
    fun scheduledOutcomes() {
        // The press was taken: busy, never a lost claim.
        assertEquals(ScheduledOutcome.BUSY, scheduledOutcomeOf(null))
        assertEquals(ScheduledOutcome.CANCELLED, scheduledOutcomeOf(FrontPageRun.Cancelled))
        assertEquals(ScheduledOutcome.LOST_CLAIM, scheduledOutcomeOf(FrontPageRun.Idle))
        assertEquals(ScheduledOutcome.FAILED, scheduledOutcomeOf(FrontPageRun.Failed(WriterFailure.UNREACHABLE)))
    }
}
