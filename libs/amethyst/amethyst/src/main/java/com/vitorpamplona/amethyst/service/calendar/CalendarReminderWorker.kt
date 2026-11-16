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
package com.vitorpamplona.amethyst.service.calendar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.nip52Calendar.CalendarReminderScanner
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_body
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_default_title
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.concurrent.TimeUnit

/**
 * Periodic scan that posts "starting soon" notifications for appointments the user has RSVP'd
 * to as ACCEPTED.
 *
 * The work is bounded — scans LocalCache (which is bounded by the relay subscription) and
 * consults [CalendarReminderLogStore] to skip events that have already been notified for. Run as
 * a 15-minute periodic worker: that's the WorkManager minimum and matches the resolution of
 * the reminder UI ("starts in ~15 min" is the smallest interval users perceive as "soon").
 *
 * The periodic chain is only kept alive while it can plausibly fire: the ACCEPTED-RSVP
 * observer in AppModules calls [schedule] when an accepted RSVP lands in LocalCache, and
 * [doWork] cancels the chain when the cache holds no accepted RSVP that could still start.
 * LocalCache is memory-only, so a WorkManager wake of a dead process always sees an empty
 * cache and can never fire a reminder — an unconditional periodic schedule would cold-start
 * the whole app graph every 15 minutes forever for zero benefit.
 */
class CalendarReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        runCatching { Amethyst.instance.resourceUsage.add(UsageKeys.workerRuns("calendarReminder"), 1) }
        val settings = calendarReminderSettings().load()
        if (!settings.enabled) {
            Log.d(TAG) { "Reminders disabled; ending periodic chain." }
            // The settings toggle re-schedules on enable; no reason to keep
            // waking the process while the feature is off.
            cancel(applicationContext)
            return Result.success()
        }
        val now = TimeUtils.now()

        // The accounts loaded in this process; a WorkManager cold start has none, and LocalCache is
        // empty then anyway.
        val accounts = Amethyst.instance.accountsCache.accounts.value.keys

        CalendarReminderScanner.scan(accounts, settings.leadMinutes, calendarReminderLog(), now) { due ->
            val title = due.title ?: loadStringRes(Res.string.calendar_reminder_default_title)
            val body = loadPluralStringRes(Res.plurals.calendar_reminder_body, due.minutesAway, due.minutesAway)
            CalendarReminderNotifier.notifyReminder(applicationContext, due.eventId, title, body, due.deepLink)
            Log.d(TAG) { "Notified ${due.eventId} (starts in ${due.minutesAway}m)" }
            true
        }

        // Nothing left that could ever fire → end the periodic chain instead of
        // waking the process every 15 minutes forever. The observers in
        // AppModules re-schedule the worker the next time a live session sees
        // an accepted RSVP or a calendar-event update.
        //
        // Decide on a FRESH cache snapshot, not the one from the start of the
        // run: an RSVP accepted while this run was scanning already fired the
        // observer, whose schedule() uses KEEP and no-ops while this chain
        // still exists — cancelling on the stale snapshot would kill the chain
        // with that RSVP's reminder permanently lost (observeNewEvents never
        // re-fires for an event that is already in cache).
        if (!CalendarReminderScanner.couldStillFire(CalendarReminderScanner.acceptedRsvpsInCache(accounts), TimeUtils.now())) {
            Log.d(TAG) { "No accepted RSVP can still fire; ending periodic chain." }
            cancel(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "CalendarReminderWorker"
        private const val WORK_NAME = "calendar_reminder_worker"

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<CalendarReminderWorker>(15, TimeUnit.MINUTES)
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG) { "schedule(): enqueueUniquePeriodicWork($WORK_NAME, 15 MIN, KEEP)" }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
