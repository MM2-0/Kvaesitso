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

import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronExpression
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ZoneIdCronZone
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Changing the time from a device in another zone (the reviewers' AuditEditZoneTest): the run
 * shown moves, the schedule keeps its zone and its weekdays there, and confirming the time shown
 * writes nothing.
 */
class FrontPageTimeEditTest {
    private val setAt = 1_700_000_000L

    private fun schedule(
        cron: String,
        zone: String = "America/New_York",
    ): ScheduledTask = ObserverSchedule.new(zone, setAt).withEnabled(true, setAt).withCron(cron, zone, setAt)

    private fun edit(
        task: ScheduledTask,
        device: String,
        now: Long,
        hour: Int,
        minute: Int,
    ) = planTimeEdit(task, hour, minute, device, ZoneIdCronZone.of(device)!!, now)

    /** The next [count] runs of [task], on [on]'s clock. */
    private fun runs(
        task: ScheduledTask,
        from: Long,
        count: Int,
        on: String,
    ): List<String> {
        val expression = CronExpression.parse(task.cron!!)!!
        val zone = ZoneIdCronZone.of(task.tz!!)!!
        var at = from
        return (1..count).map {
            at = expression.nextAfter(at, zone)!!
            Instant.ofEpochSecond(at).atZone(ZoneId.of(on)).let { "${it.dayOfWeek.toString().take(3)} ${it.toLocalTime()}" }
        }
    }

    @Test
    fun confirmingWeekdaysFromAucklandChangesNothing() {
        val now = Instant.parse("2026-11-11T12:00:00Z").epochSecond
        val weekdays = schedule("0 6 * * 1-5")
        // Auckland shows midnight; confirming it writes nothing: no new event, no new set time.
        assertEquals(0 to 0, editableTime(weekdays, "Pacific/Auckland", ZoneIdCronZone.of("Pacific/Auckland"), now))
        assertEquals(TimeEdit.Unchanged, edit(weekdays, "Pacific/Auckland", now, 0, 0))
    }

    @Test
    fun anHourLaterFromAucklandIsSevenInNewYorkOnTheSameWeekdays() {
        val now = Instant.parse("2026-11-11T12:00:00Z").epochSecond
        val changed = assertIs<TimeEdit.Change>(edit(schedule("0 6 * * 1-5"), "Pacific/Auckland", now, 1, 0)).task
        assertEquals("0 7 * * 1-5", changed.cron)
        assertEquals("America/New_York", changed.tz)
        assertEquals(now, changed.cronSetAt)
        assertEquals(listOf("THU 07:00", "FRI 07:00", "MON 07:00", "TUE 07:00", "WED 07:00"), runs(changed, now, 5, "America/New_York"))
    }

    @Test
    fun aNudgeFromLondonInTheMismatchWeekKeepsNewYorksClock() {
        val now = Instant.parse("2026-03-20T12:00:00Z").epochSecond
        val daily = schedule("0 6 * * *")
        assertEquals(10 to 0, editableTime(daily, "Europe/London", ZoneIdCronZone.of("Europe/London"), now))
        val changed = assertIs<TimeEdit.Change>(edit(daily, "Europe/London", now, 10, 15)).task
        assertEquals("15 6 * * *", changed.cron)
        assertEquals("America/New_York", changed.tz)
        // From April: 06:15 in New York, not 05:15.
        assertEquals(listOf("WED 06:15", "THU 06:15"), runs(changed, Instant.parse("2026-04-01T00:00:00Z").epochSecond, 2, "America/New_York"))
    }

    @Test
    fun inTheSchedulesOwnZoneTheFieldsChangeInPlace() {
        val now = Instant.parse("2026-11-11T12:00:00Z").epochSecond
        val weekdays = schedule("0 6 * * 1-5")
        assertEquals(6 to 0, editableTime(weekdays, "America/New_York", ZoneIdCronZone.of("America/New_York"), now))
        assertEquals("30 6 * * 1-5", assertIs<TimeEdit.Change>(edit(weekdays, "America/New_York", now, 6, 30)).task.cron)
        // The same time: nothing, even when the expression is written another way.
        assertEquals(TimeEdit.Unchanged, edit(schedule("0 6 * * 1,2,3,4,5"), "America/New_York", now, 6, 0))
    }

    @Test
    fun aScheduleTheEditorCantShowIsLeftAlone() {
        val now = Instant.parse("2026-11-11T12:00:00Z").epochSecond
        assertEquals(TimeEdit.NotEditable, edit(schedule("0 6,18 * * *"), "America/New_York", now, 7, 0))
        assertEquals(TimeEdit.NotEditable, edit(schedule("0 6 * * *", "+05:00"), "America/New_York", now, 7, 0))
    }

    @Test
    fun aTimeTheDeviceSkipsThatDayIsRefused() {
        // Saturday 21:30 in New York is 02:30 on Sunday 29 March in London, the night its clocks skip 01:00 to 02:00.
        val now = Instant.parse("2026-03-28T12:00:00Z").epochSecond
        assertEquals(TimeEdit.NotExpressible, edit(schedule("30 21 * * 6"), "Europe/London", now, 1, 45))
    }
}
