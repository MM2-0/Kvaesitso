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

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageMorning
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageSchedule
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageScheduleWatcher
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageScheduler
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * The morning edition on Android: one unique WorkManager job per account, due at the next
 * occurrence of the synced schedule's cron in its zone, network-constrained, and only where the
 * reader turned on "Print from this device" ([FrontPageScheduleWatcher] cancels it otherwise).
 * Which of the reader's devices prints an occurrence is decided by its claim, in the run.
 * WorkManager keeps it across reboots. Each run schedules the next, after itself.
 *
 * The job that waits and the print that runs are kept apart: a reschedule never replaces a job
 * that is running (that would cancel a paid print), and a job already waiting for the same
 * instant is left alone (every cold start reschedules), so it is replaced only when the time
 * changed. The instant a job waits for is in its tags.
 */
class AndroidFrontPageScheduler(
    private val context: Context,
) : FrontPageScheduler {
    override fun reschedule(
        account: HexKey,
        schedule: FrontPageSchedule,
    ) {
        val manager = WorkManager.getInstance(context)
        val name = workName(account)
        val now = TimeUtils.now()
        val next = if (schedule.enabled) schedule.nextRun(now) else null
        val jobs =
            try {
                manager.getWorkInfosForUniqueWork(name).get()
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the front page job", e)
                emptyList()
            }
        when (jobAction(jobs.map { it.state to it.tags }, next)) {
            JobAction.LEAVE -> {}
            JobAction.CANCEL -> manager.cancelUniqueWork(name)
            JobAction.REPLACE -> {
                val at = next ?: return
                manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request(account, at - now, dueTag(at), schedule = schedule))
                Log.d(TAG) { "Next front page for ${account.take(8)} in ${(at - now) / 60} min" }
            }
        }
    }

    /** From a run that is ending: the next one goes after it, without cancelling it. */
    fun scheduleAfterRun(
        account: HexKey,
        schedule: FrontPageSchedule,
    ) {
        val now = TimeUtils.now()
        val next = (if (schedule.enabled) schedule.nextRun(now) else null) ?: return
        WorkManager.getInstance(context).enqueueUniqueWork(workName(account), ExistingWorkPolicy.APPEND_OR_REPLACE, request(account, next - now, dueTag(next), schedule = schedule))
        Log.d(TAG) { "Next front page for ${account.take(8)} in ${(next - now) / 60} min" }
    }

    /** A removed account: every job goes, a running one too. */
    override fun forget(account: HexKey) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(workName(account))
        manager.cancelUniqueWork(catchUpName(account))
    }

    /** A morning missed while the phone was off or the app closed: once, now, beside the next one. */
    override fun runNow(account: HexKey) {
        WorkManager.getInstance(context).enqueueUniqueWork(catchUpName(account), ExistingWorkPolicy.KEEP, request(account, 0, null, catchUp = true))
    }

    private fun request(
        account: HexKey,
        delaySec: Long,
        due: String?,
        catchUp: Boolean = false,
        schedule: FrontPageSchedule? = null,
    ) = OneTimeWorkRequestBuilder<FrontPageWorker>()
        .setInitialDelay(delaySec.coerceAtLeast(0), TimeUnit.SECONDS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(
            workDataOf(
                FrontPageWorker.KEY_ACCOUNT to account,
                FrontPageWorker.KEY_CATCH_UP to catchUp,
                // The schedule this job was made for: the next job keeps it when the run learns nothing new.
                FrontPageWorker.KEY_CRON to schedule?.takeIf { it.enabled }?.cron,
                FrontPageWorker.KEY_ZONE to schedule?.takeIf { it.enabled }?.zone,
            ),
        ).addTag(TAG)
        .apply { if (due != null) addTag(due) }
        .build()

    companion object {
        const val TAG = "FrontPageScheduler"

        fun workName(account: HexKey) = "front_page_${account.lowercase()}"

        fun catchUpName(account: HexKey) = "front_page_catchup_${account.lowercase()}"

        /** The instant a job waits for, as a tag: a reschedule to the same instant leaves it. */
        fun dueTag(epochSec: Long) = "front_page_due_$epochSec"
    }
}

/** What a reschedule does to the job already there. */
enum class JobAction { LEAVE, CANCEL, REPLACE }

/**
 * A reschedule to [next] (null: off) given the account's [jobs] (their states and tags): a
 * running job is left alone (it schedules the next one itself as it ends, and replacing it would
 * cancel a print), a job already waiting for [next] too (every cold start reschedules); otherwise
 * the job is cancelled when off, or replaced.
 */
fun jobAction(
    jobs: List<Pair<WorkInfo.State, Set<String>>>,
    next: Long?,
): JobAction {
    if (jobs.any { it.first == WorkInfo.State.RUNNING }) return JobAction.LEAVE
    if (next == null) return if (jobs.any { !it.first.isFinished }) JobAction.CANCEL else JobAction.LEAVE
    val due = AndroidFrontPageScheduler.dueTag(next)
    return if (jobs.any { it.first == WorkInfo.State.ENQUEUED && due in it.second }) JobAction.LEAVE else JobAction.REPLACE
}

/**
 * Prints one account's morning edition, then schedules tomorrow's. It is a plain worker, not a
 * foreground one: Android 12+ refuses to start a foreground service from the background, and a
 * print (desks, then the model) fits inside WorkManager's ten-minute window. A run that would
 * outlast it stops itself first.
 */
class FrontPageWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val account = inputData.getString(KEY_ACCOUNT) ?: return Result.success()
        val catchUp = inputData.getBoolean(KEY_CATCH_UP, false)
        val startedAt = TimeUtils.now()
        val app = Amethyst.instance
        val printers = app.frontPagePrinters
        var loaded: Account? = null
        try {
            // A cold process has no account loaded: load the ones that can sign, as the
            // scheduled-post worker does. One that cannot load is the reader's to fix.
            val cache = app.accountsCache
            if (account !in cache.accounts.value) cache.loadAllWritableAccounts(LocalPreferences)
            loaded = cache.accounts.value[account]
            if (loaded == null) {
                if (printers.schedules.get(account).enabled) app.frontPageNotifier.needsYou(account)
                return Result.success()
            }

            // Whether this device prints, the claim, the occurrence's edition: all decided there,
            // within the time WorkManager gives a run, `ran` included.
            val deadline = startedAt + RUN_LIMIT_MS / 1000
            val outcome = withTimeoutOrNull(HARD_LIMIT_MS) { printers.of(loaded).printScheduled(deadline = deadline) }
            Log.d(TAG) { "Morning edition for ${account.take(8)}: ${outcome ?: "timed out"}" }
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Morning edition failed", e)
            return Result.success()
        } finally {
            // The next one, whatever happened to this one: from the synced schedule as read now.
            // A catch-up run leaves the regular job alone.
            if (!catchUp) {
                try {
                    // Nothing known of the synced schedule (its relays didn't answer, NO_QUORUM): the
                    // next job keeps the schedule this one was made for, so the chain goes on.
                    val made = madeFor(inputData.getString(KEY_CRON), inputData.getString(KEY_ZONE))
                    val next = loaded?.let { printers.morning.scheduleHere(it, fallback = made) } ?: FrontPageSchedule()
                    app.frontPageScheduler.scheduleAfterRun(account, next)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not schedule the next front page", e)
                }
            }
        }
    }

    companion object {
        const val KEY_ACCOUNT = "account"
        const val KEY_CATCH_UP = "catch_up"
        const val KEY_CRON = "cron"
        const val KEY_ZONE = "zone"

        /** The schedule a job was made for, from its data; off when it carries none (a job from before). */
        fun madeFor(
            cron: String?,
            zone: String?,
        ): FrontPageSchedule = if (cron == null || zone == null) FrontPageSchedule() else FrontPageSchedule(true, cron, zone)

        private const val TAG = "FrontPageWorker"

        /**
         * Under WorkManager's ten minutes: what the run plans for (the random wait, three claim
         * rounds, the print, `ran` and its retries: [FrontPageMorning.OVERHEAD_SECONDS] plus the print).
         */
        private const val RUN_LIMIT_MS = 9 * 60 * 1000L + 30 * 1000L

        /** The run's own deadline, should it overrun anyway: before WorkManager kills it. */
        private const val HARD_LIMIT_MS = 9 * 60 * 1000L + 50 * 1000L
    }
}
