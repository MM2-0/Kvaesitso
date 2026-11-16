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

import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.planScheduleEdit
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronExpression
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import com.vitorpamplona.quartz.nipFBScheduledTasks.SimpleCron
import com.vitorpamplona.quartz.nipFBScheduledTasks.ZoneIdCronZone
import com.vitorpamplona.quartz.nipFBScheduledTasks.tags.CronTag
import com.vitorpamplona.quartz.nipFBScheduledTasks.tags.TaskTag
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrontPageMorningTest {
    private val now = 1_794_394_800L // 2026-11-11T11:00:00Z, 06:00 in New York

    private val task = ObserverSchedule.new("America/New_York").withEnabled(true, 0).withCron("0 6 * * *", "America/New_York", 0)

    private val on = FrontPageSchedule.daily(true, 7, 30)
    private val off = FrontPageSchedule()

    private suspend fun readable(task: ScheduledTask): SyncedSchedule.Readable {
        val signer = NostrSignerInternal(KeyPair())
        val event: ScheduledTaskEvent = signer.sign(ObserverSchedule.build(task, signer, previous = null, now = now))
        return SyncedSchedule.Readable(event, task)
    }

    @Test
    fun aDevicePrintsOnlyWhereTheReaderTurnedItOn() =
        runTest {
            val synced = readable(task)
            assertEquals(FrontPageSchedule(true, "0 6 * * *", "America/New_York"), scheduleForThisDevice(synced, on, canPrintHere = true))
            // Not turned on here, or can't print alone here, or nowhere to claim: nothing scheduled here.
            assertFalse(scheduleForThisDevice(synced, off, canPrintHere = true)!!.enabled)
            assertFalse(scheduleForThisDevice(synced, on, canPrintHere = false)!!.enabled)
            assertFalse(scheduleForThisDevice(synced, on, canPrintHere = true, hasWriteRelays = false)!!.enabled)
            // Paused for everyone, a task this app doesn't run, a cron it can't read: nobody here.
            assertFalse(scheduleForThisDevice(readable(task.withEnabled(false, 0)), on, true)!!.enabled)
            assertFalse(scheduleForThisDevice(readable(task.replacing(TaskTag::isTag, TaskTag.assemble("digest"))), on, true)!!.enabled)
            assertFalse(scheduleForThisDevice(readable(task.withCron("@daily", "UTC", 0)), on, true)!!.enabled)
        }

    @Test
    fun aZoneThatIsMissingOrUnknownIsNeverReadInTheDevicesZone() =
        runTest {
            val noZone = task.replacing(CronTag::isTag, arrayOf("cron", "0 6 * * *"))
            val offset = task.withCron("0 6 * * *", "+05:00", 0)
            val mars = task.withCron("0 6 * * *", "Mars/Olympus_Mons", 0)
            for (unreadable in listOf(noZone, offset, mars, task.withCron("0 6 * * *", "UTC+5", 0))) {
                assertFalse(scheduleForThisDevice(readable(unreadable), on, true)!!.enabled)
                assertNull(FrontPageSchedule(true, "0 6 * * *", unreadable.tz).nextRun(now))
                val view = viewOf(readable(unreadable), on, canPrintHere = true, now = now, deviceId = "America/New_York")
                assertEquals(ScheduleState.UNREADABLE, view.state)
                assertNull(view.editableTime)
            }
        }

    @Test
    fun nothingIsMadeUpBeforeTheScheduleIsKnown() {
        // No synced schedule seen: decide nothing for the job, and show "not set up", switched off.
        assertNull(scheduleForThisDevice(SyncedSchedule.Unknown, on, canPrintHere = true))
        val view = viewOf(SyncedSchedule.Unknown, on, canPrintHere = true, now = now)
        assertEquals(ScheduleState.NOT_SET_UP, view.state)
        assertFalse(view.enabled)
        assertNull(view.words)
        assertTrue(view.printsHere)
    }

    @Test
    fun aSignerThatDidNotOpenItIsNotAnUnreadableSchedule() =
        runTest {
            val event = readable(task).event
            val locked = viewOf(SyncedSchedule.Locked(event), on, true, now = now)
            assertEquals(ScheduleState.CANT_DECRYPT, locked.state)
            assertTrue(locked.retryable)
            val refused = viewOf(SyncedSchedule.CantDecrypt(event, retryable = true), on, true, now = now)
            assertEquals(ScheduleState.CANT_DECRYPT, refused.state)
            // A read-only login can't open it at all: nothing to retry.
            assertFalse(viewOf(SyncedSchedule.CantDecrypt(event, retryable = false), on, true, now = now).retryable)
            assertEquals(ScheduleState.UNREADABLE, viewOf(SyncedSchedule.Unreadable(event), on, true, now = now).state)
            assertFalse(scheduleForThisDevice(SyncedSchedule.CantDecrypt(event, true), on, true)!!.enabled)
        }

    @Test
    fun theViewSaysWhoPrintedLastAndWarns() =
        runTest {
            val printed = ScheduledTaskClaims.finished(ScheduledTaskClaims.claimed(task, now, "desk", "Desktop (Linux)", now), now + 60)
            val view = viewOf(readable(printed), on, canPrintHere = true, now = now + 3_600)
            assertEquals(ScheduleState.READY, view.state)
            assertTrue(view.enabled)
            assertTrue(view.printsHere)
            assertEquals("Desktop (Linux)", view.lastBy)
            assertEquals(now + 60, view.lastRan)
            assertEquals(3_540L / 60 * 60, view.lastRanAgo)
            assertNull(view.missedSince)
            // Two days later with no run since: missed, on every device.
            assertEquals(now + 60, viewOf(readable(printed), off, canPrintHere = false, now = now + 2 * 86_400).missedSince)
            // Never ran: no warning.
            assertNull(viewOf(readable(task), off, false, now = now + 9 * 86_400).missedSince)
        }

    @Test
    fun theNameIsOnlyForTheClaimThatRan() =
        runTest {
            // Yesterday's run by the desktop, today's claim by the phone still running: no name.
            val yesterday = ScheduledTaskClaims.finished(ScheduledTaskClaims.claimed(task, now - 86_400, "desk", "Desktop (Linux)", now - 86_400), now - 86_000)
            val claimedToday = ScheduledTaskClaims.claimed(yesterday, now, "phone", "Pixel 8", now + 10)
            val view = viewOf(readable(claimedToday), on, true, now = now + 600)
            assertNull(view.lastBy)
            assertEquals(now - 86_000, view.lastRan)
        }

    @Test
    fun agoIsRoundedToWhatTheLineSays() {
        assertEquals(0, agoBucket(59))
        assertEquals(120, agoBucket(179))
        assertEquals(7_200, agoBucket(2 * 3_600 + 3_599))
        assertEquals(86_400, agoBucket(86_400 + 3_600))
    }

    @Test
    fun schedulesAreToldInWords() {
        val ny = ZoneIdCronZone.of("America/New_York")!!
        val auckland = ZoneIdCronZone.of("Pacific/Auckland")!!

        fun words(
            cron: String,
            deviceId: String = "America/New_York",
        ) = describeCron(CronExpression.parse(cron)!!, "America/New_York", ny, deviceId, ZoneIdCronZone.of(deviceId), now)
        assertEquals(CronWords.Days(SimpleCron(6, 0, setOf(1, 2, 3, 4, 5))), words("0 6 * * 1-5"))
        assertEquals(CronWords.Days(SimpleCron(18, 0)), words("0 18 * * *"))
        // From Auckland: midnight, Tuesday to Saturday.
        assertEquals(CronWords.Days(SimpleCron(0, 0, setOf(2, 3, 4, 5, 6))), words("0 6 * * 1-5", "Pacific/Auckland"))
        assertEquals(CronWords.TimesADay(listOf(6 to 0, 18 to 0)), words("0 6,18 * * *"))
        assertEquals(CronWords.EveryHours(6, 15), words("15 */6 * * *"))
        assertEquals(CronWords.Monthly(1, 6, 0), words("0 6 1 * *"))
        assertEquals(CronWords.Raw("0 6 * 1 1"), words("0 6 * 1 1"))
        // Moving them to Auckland's clock would cross midnight: said on New York's clock, named.
        assertEquals(CronWords.TimesADay(listOf(6 to 0, 18 to 0), "America/New_York"), words("0 6,18 * * *", "Pacific/Auckland"))
        assertEquals(auckland.offsetSecondsAt(now), 13 * 3_600)
    }

    @Test
    fun anEditNeverStartsFromNothingItCouldNotRead() {
        val change: (ScheduledTask?) -> ScheduledTask? = { (it ?: ObserverSchedule.new("UTC")).withEnabled(true, 0) }
        // Not more than half of the write relays answered: nothing, whatever the cache holds.
        assertEquals(null to ScheduleEdit.Unreachable, planScheduleEdit(hasEvent = false, current = null, quorum = false, transform = change))
        assertEquals(null to ScheduleEdit.Unreachable, planScheduleEdit(hasEvent = true, current = task, quorum = false, transform = change))
        // An event that couldn't be read.
        assertEquals(null to ScheduleEdit.Unreadable, planScheduleEdit(hasEvent = true, current = null, quorum = true, transform = change))
        // The relays answered, none has one: the first one is written.
        val (first, saved) = planScheduleEdit(hasEvent = false, current = null, quorum = true, transform = change)
        assertTrue(first!!.enabled)
        assertIs<ScheduleEdit.Saved>(saved)
        // An edit that changes nothing writes nothing.
        assertEquals(null to ScheduleEdit.Unchanged, planScheduleEdit(hasEvent = true, current = task, quorum = true) { it })
        assertEquals(null to ScheduleEdit.Unchanged, planScheduleEdit(hasEvent = true, current = task, quorum = true) { null })
    }

    @Test
    fun theCronIsReadInItsZone() {
        val ny = ZoneId.of("America/New_York")
        val schedule = FrontPageSchedule(true, "0 6 * * 1-5", "America/New_York")
        // Friday 07:00 in New York: the next is Monday 06:00 there; the last was Friday's.
        val friday7 = ZonedDateTime.of(LocalDateTime.parse("2026-11-13T07:00"), ny).toEpochSecond()
        assertEquals(ZonedDateTime.of(LocalDateTime.parse("2026-11-16T06:00"), ny).toEpochSecond(), schedule.nextRun(friday7))
        assertEquals(friday7 - 3_600, schedule.lastRun(friday7))
        // The old local `on|7|30`: no zone, so it never runs as it is.
        assertEquals("30 7 * * *", FrontPageSchedule.daily(true, 7, 30).cron)
        assertNull(FrontPageSchedule.daily(true, 7, 30).nextRun(friday7))
        assertNull(FrontPageSchedule(true, "@daily", "UTC").nextRun(friday7))
    }
}
