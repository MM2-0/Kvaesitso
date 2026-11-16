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

import com.vitorpamplona.quartz.nipFBScheduledTasks.CronExpression
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** What keeps The Observer from showing a stale view while the app stays open. */
class FrontPageStaleViewTest {
    private val utc = CronZone.fixed(0)

    /** 2026-11-12T06:00:00Z. */
    private val six = 1_794_463_200L

    @Test
    fun theSitesAreReadAgainAfterEachMorningAndBeforeItCouldBeCalledMissed() {
        val daily = assertNotNull(CronExpression.parse("0 6 * * *"))
        // Overnight, before six: the next re-read is twenty minutes after it.
        assertEquals(six + SITE_REREAD_AFTER_DUE_SECONDS, nextSiteReread(daily, utc, six - 5 * 3_600))
        // Just after six: still that one.
        assertEquals(six + SITE_REREAD_AFTER_DUE_SECONDS, nextSiteReread(daily, utc, six + 60))
        // Past it: once more before the missed-run warning could show.
        val beforeMissed = six + ScheduledTask.GRACE_SECONDS - SITE_REREAD_BEFORE_MISSED_SECONDS
        assertEquals(beforeMissed, nextSiteReread(daily, utc, six + SITE_REREAD_AFTER_DUE_SECONDS))
        assertTrue(beforeMissed < six + ScheduledTask.GRACE_SECONDS)
        // Then tomorrow's morning.
        assertEquals(six + 86_400 + SITE_REREAD_AFTER_DUE_SECONDS, nextSiteReread(daily, utc, beforeMissed))
    }

    @Test
    fun aDeletedCharacterCancelsTheWriteOfTheOneTypedBeforeIt() =
        runTest {
            val written = mutableListOf<String>()
            val write = DebouncedWrite(backgroundScope, SETTINGS_PAUSE_MS)
            // The reader types a character after the model the schedule names...
            write.submit { written += "anthropic/claude-sonnet-4.5x" }
            assertTrue(write.pending)
            advanceTimeBy(SETTINGS_PAUSE_MS / 2)
            // ...and deletes it: the name is the schedule's again, nothing to write.
            write.submit(null)
            assertFalse(write.pending)
            advanceTimeBy(SETTINGS_PAUSE_MS * 2)
            runCurrent()
            assertEquals(emptyList(), written)
        }

    @Test
    fun onlyTheLastPickIsWritten() =
        runTest {
            val written = mutableListOf<String>()
            val write = DebouncedWrite(backgroundScope, SETTINGS_PAUSE_MS)
            write.submit { written += "a" }
            advanceTimeBy(SETTINGS_PAUSE_MS / 2)
            write.submit { written += "ab" }
            advanceTimeBy(SETTINGS_PAUSE_MS * 2)
            runCurrent()
            assertEquals(listOf("ab"), written)
        }

    @Test
    fun aWriteThatIsRunningIsStillPending() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val write = DebouncedWrite(backgroundScope, SETTINGS_PAUSE_MS)
            write.submit { gate.await() }
            advanceTimeBy(SETTINGS_PAUSE_MS + 1)
            runCurrent()
            // Running now: a newer pick doesn't stop it, and it is still pending.
            write.submit(null)
            assertTrue(write.pending)
            gate.complete(Unit)
            runCurrent()
            assertFalse(write.pending)
        }

    @Test
    fun aPickBackDuringARunningWriteIsWrittenAfterIt() =
        runTest {
            val written = mutableListOf<String>()
            val gate = CompletableDeferred<Unit>()
            val write = DebouncedWrite(backgroundScope, SETTINGS_PAUSE_MS)
            write.submit {
                gate.await()
                written += "b"
            }
            advanceTimeBy(SETTINGS_PAUSE_MS + 1)
            runCurrent()
            // The reader picks the saved model back while "b" is being written: no write of its
            // own is needed, but "b" would stay, so "a" is written after it.
            write.submit(needed = false) { written += "a" }
            assertTrue(write.pending)
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf("b", "a"), written)
            assertFalse(write.pending)
        }

    @Test
    fun aPickThatNeedsNoWriteWritesNothingWhenNothingRuns() =
        runTest {
            val written = mutableListOf<String>()
            val write = DebouncedWrite(backgroundScope, SETTINGS_PAUSE_MS)
            write.submit { written += "x" }
            write.submit(needed = false) { written += "saved" }
            assertFalse(write.pending)
            advanceTimeBy(SETTINGS_PAUSE_MS * 2)
            runCurrent()
            assertEquals(emptyList(), written)
        }

    @Test
    fun aCancelBeforeThePrintStartsEndsItCancelled() =
        runTest {
            val slot = PrintSlot()
            assertTrue(slot.tryHold())
            // A scheduled run holds the press through its claim; no print is running yet.
            slot.cancel()
            assertTrue(slot.isCancelRequested)
            var printed = false
            val ended = slot.runCancellable("cancelled") { "printed".also { printed = true } }
            assertEquals("cancelled", ended)
            assertFalse(printed)
            slot.release()
            // The next holder starts clean.
            assertTrue(slot.tryHold())
            assertFalse(slot.isCancelRequested)
            assertEquals("printed", slot.runCancellable("cancelled") { "printed" })
            slot.release()
            // A cancel with nobody holding the press leaves nothing behind.
            slot.cancel()
            assertTrue(slot.tryHold())
            assertFalse(slot.isCancelRequested)
            slot.release()
        }

    @Test
    fun thePressSaysWhenItIsTakenAndLetGo() {
        val said = mutableListOf<Boolean>()
        val slot = PrintSlot { said += it }
        assertTrue(slot.tryHold())
        // A second holder is refused, and says nothing.
        assertFalse(slot.tryHold())
        slot.release()
        assertTrue(slot.tryHold())
        slot.release()
        assertEquals(listOf(true, false, true, false), said)
    }
}
