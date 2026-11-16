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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrontPagePrintJobTest {
    private val reader = "a".repeat(64)

    private class Host : FrontPagePrintHost {
        val calls = mutableListOf<String>()

        override fun printStarted(account: HexKey) {
            calls += "started"
        }

        override fun printEnded(account: HexKey) {
            calls += "ended"
        }
    }

    private var clock = 1_800_000_000L

    private fun tracker(
        host: FrontPagePrintHost,
        seen: MutableList<FrontPagePrintJob?> = mutableListOf(),
    ) = FrontPagePrintJobTracker(reader, host, { seen += it }) { clock }

    @Test
    fun aManualPrintStartsAndEndsTheHost() {
        val host = Host()
        val jobs = tracker(host)
        jobs.started(PrintTrigger.MANUAL)
        assertEquals(listOf("started"), host.calls)
        assertTrue(jobs.job.value!!.runningManual)
        jobs.finished(PrintTrigger.MANUAL)
        assertEquals(listOf("started", "ended"), host.calls)
    }

    @Test
    fun aScheduledPrintLeavesTheHostAlone() {
        val host = Host()
        val jobs = tracker(host)
        jobs.started(PrintTrigger.SCHEDULED)
        jobs.finished(PrintTrigger.SCHEDULED)
        assertTrue(host.calls.isEmpty())
        assertFalse(jobs.job.value!!.runningManual)
    }

    @Test
    fun theJobChangesOnlyWhenItsStageDoes() {
        val seen = mutableListOf<FrontPagePrintJob?>()
        val jobs = tracker(Host(), seen)
        jobs.started(PrintTrigger.MANUAL)
        jobs.step(PrintStep.Pull(1, 4, 10))
        jobs.step(PrintStep.Pull(2, 4, 30))
        jobs.step(PrintStep.Write(1, 400))
        jobs.step(PrintStep.Write(1, 800))
        jobs.step(PrintStep.Write(1, 1200))
        jobs.step(PrintStep.Check(1))
        assertEquals(
            listOf(PrintJobStage.Probe, PrintJobStage.Reading(1, 4), PrintJobStage.Reading(2, 4), PrintJobStage.Writing(1), PrintJobStage.Checking),
            seen.map { it!!.stage },
        )
        assertEquals(0.5f, PrintJobStage.Reading(2, 4).fraction)
    }

    @Test
    fun aCleanPageEndsReadyUntilDismissed() {
        val seen = mutableListOf<FrontPagePrintJob?>()
        val jobs = tracker(Host(), seen)
        jobs.started(PrintTrigger.MANUAL)
        clock += 95
        val kept = SavedEdition(day = "2026-11-12", code = "x", printedAt = clock, model = "m", headline = "Relays agree")
        val end = jobs.ended(FrontPageRun.Done(printed()), kept)
        assertEquals(PrintJobEnd.Ready("2026-11-12", "Relays agree"), end)
        assertEquals(clock, jobs.job.value!!.endedAt)
        assertFalse(jobs.job.value!!.running)
        jobs.dismiss()
        assertNull(jobs.job.value)
        assertNull(seen.last())
    }

    @Test
    fun aFailureSaysWhyAndTellsAReaderWhoIsNotLooking() {
        val jobs = tracker(Host())
        jobs.started(PrintTrigger.MANUAL)
        val end = jobs.ended(FrontPageRun.Failed(WriterFailure.REJECTED, 401), null)
        assertEquals(PrintJobEnd.Failed(WriterFailure.REJECTED, 401), end)
        assertTrue(FrontPagePrintJobTracker.tellsReaderOfFailure(end, PrintTrigger.MANUAL, watched = 0))
        // On the front page itself the screen says it; nothing goes outside the app.
        assertFalse(FrontPagePrintJobTracker.tellsReaderOfFailure(end, PrintTrigger.MANUAL, watched = 1))
        assertFalse(FrontPagePrintJobTracker.tellsReaderOfFailure(end, PrintTrigger.SCHEDULED, watched = 0))
        assertFalse(FrontPagePrintJobTracker.tellsReaderOfFailure(PrintJobEnd.Ready("d", null), PrintTrigger.MANUAL, watched = 0))
    }

    @Test
    fun aRunningJobIsNotDismissedAndACancelDropsIt() {
        val jobs = tracker(Host())
        jobs.started(PrintTrigger.MANUAL)
        jobs.dismiss()
        assertTrue(jobs.job.value!!.running)
        jobs.dropped()
        assertNull(jobs.job.value)
    }

    @Test
    fun notReadyAndRefusedAreEnds() {
        assertEquals(PrintJobEnd.NotReady, endOf(FrontPageRun.Done(PrintResult.NotReady(ProbeVerdict.PROJECTION_PENDING)), null, null))
        assertNull(endOf(FrontPageRun.Idle, null, null))
    }

    private fun printed() = PrintResult.Printed("<html></html>", Fixtures.corpus(emptyMap()), emptyList(), 0, null, 1)

    @Test
    fun theXHidesARunningPrintWithoutStoppingIt() {
        val tracker = FrontPagePrintJobTracker("reader", now = { 100 })
        tracker.started(PrintTrigger.SCHEDULED)
        tracker.hide()
        assertTrue(tracker.job.value!!.hidden)
        assertTrue(tracker.job.value!!.running)
        // Opening The Observer brings it back.
        tracker.unhide()
        assertFalse(tracker.job.value!!.hidden)
        // Hidden, then ended: the end shows.
        tracker.hide()
        tracker.ended(FrontPageRun.Failed(WriterFailure.UNREACHABLE), null)
        assertFalse(tracker.job.value!!.hidden)
        // An ended print isn't hidden: × dismisses it.
        tracker.hide()
        assertFalse(tracker.job.value!!.hidden)
    }

    @Test
    fun aKeptPageStaysRunningWhilePublishing() {
        val tracker = FrontPagePrintJobTracker("reader", now = { 100 })
        tracker.started(PrintTrigger.MANUAL)
        tracker.publishing()
        assertEquals(PrintJobStage.Publishing, tracker.job.value!!.stage)
        assertTrue(tracker.job.value!!.runningManual)
    }
}
