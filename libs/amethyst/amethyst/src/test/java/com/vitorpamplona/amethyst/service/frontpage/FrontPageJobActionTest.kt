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
package com.vitorpamplona.amethyst.service.frontpage

import androidx.work.WorkInfo
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageSchedule
import org.junit.Assert.assertEquals
import org.junit.Test

class FrontPageJobActionTest {
    private val sixTomorrow = 1_794_481_200L
    private val waiting = WorkInfo.State.ENQUEUED to setOf(AndroidFrontPageScheduler.TAG, AndroidFrontPageScheduler.dueTag(sixTomorrow))

    @Test
    fun aRunningPrintIsNeverReplaced() {
        // Cold start: the app reschedules while the worker that woke it is printing.
        assertEquals(JobAction.LEAVE, jobAction(listOf(WorkInfo.State.RUNNING to setOf(AndroidFrontPageScheduler.TAG)), sixTomorrow))
        // Turned off mid-print: the print goes on; it schedules nothing after itself.
        assertEquals(JobAction.LEAVE, jobAction(listOf(WorkInfo.State.RUNNING to emptySet()), null))
    }

    @Test
    fun aJobWaitingForTheSameRunIsLeftAlone() {
        assertEquals(JobAction.LEAVE, jobAction(listOf(waiting), sixTomorrow))
        // Another time: replaced.
        assertEquals(JobAction.REPLACE, jobAction(listOf(waiting), sixTomorrow + 900))
        assertEquals(JobAction.REPLACE, jobAction(emptyList(), sixTomorrow))
        assertEquals(JobAction.REPLACE, jobAction(listOf(WorkInfo.State.SUCCEEDED to waiting.second), sixTomorrow))
    }

    @Test
    fun offCancelsOnlyAJobThatIsStillThere() {
        assertEquals(JobAction.CANCEL, jobAction(listOf(waiting), null))
        assertEquals(JobAction.LEAVE, jobAction(listOf(WorkInfo.State.SUCCEEDED to emptySet()), null))
        assertEquals(JobAction.LEAVE, jobAction(emptyList(), null))
    }

    @Test
    fun aJobCarriesTheScheduleItWasMadeFor() {
        // A run that learns nothing of the synced schedule (NO_QUORUM) schedules this one again.
        assertEquals(FrontPageSchedule(true, "0 6 * * 1-5", "America/New_York"), FrontPageWorker.madeFor("0 6 * * 1-5", "America/New_York"))
        // A job queued before it carried one: off, as before.
        assertEquals(FrontPageSchedule(), FrontPageWorker.madeFor(null, null))
        assertEquals(FrontPageSchedule(), FrontPageWorker.madeFor("0 6 * * *", null))
    }
}
