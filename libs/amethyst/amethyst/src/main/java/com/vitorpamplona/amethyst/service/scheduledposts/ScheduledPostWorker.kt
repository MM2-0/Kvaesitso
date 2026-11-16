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
package com.vitorpamplona.amethyst.service.scheduledposts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostPublisher
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Scans the scheduled-post store and publishes posts whose publish time has arrived.
 *
 *  - schedule(context):       periodic, every 15 min (WorkManager minimum). Only
 *                             enqueued while the store holds a PENDING post — the
 *                             store observer in AppModules schedules it when a
 *                             pending post appears and cancels it when the last
 *                             one drains, so the worker never wakes the process
 *                             with nothing to publish.
 *  - scheduleCatchUp(context): one-time, to flush posts that came due while the
 *                              device was off or while WorkManager was deferred
 *                              by Doze.
 */
class ScheduledPostWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    init {
        // Logs every worker instantiation. Without this, "doWork never ran" looks
        // identical to "constructor never invoked" — and the latter means the OS
        // (Doze, battery-opt, JobScheduler quotas) never woke us at all.
        Log.d(TAG) { "Worker instantiated (runAttempt=${workerParams.runAttemptCount}, tags=${workerParams.tags})" }
    }

    companion object {
        private const val TAG = "ScheduledPostWorker"
        private const val WORK_NAME = "scheduled_post_worker"
        private const val WORK_NAME_CATCH_UP = "scheduled_post_worker_catch_up"

        fun schedule(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<ScheduledPostWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG) {
                "schedule(): enqueueUniquePeriodicWork($WORK_NAME, 15 MIN, KEEP) — KEEP policy preserves any existing schedule"
            }
        }

        fun scheduleCatchUp(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val request =
                OneTimeWorkRequestBuilder<ScheduledPostWorker>()
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_CATCH_UP,
                ExistingWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG) { "scheduleCatchUp(): enqueueUniqueWork($WORK_NAME_CATCH_UP, KEEP)" }
        }

        /**
         * Ends the 15-minute periodic chain (keeps any catch-up run). Called by the
         * store observer in AppModules when the last PENDING post drains, so the
         * worker doesn't keep waking the process with nothing to publish.
         */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG) { "cancelPeriodic(): cancelled periodic worker" }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_CATCH_UP)
            Log.d(TAG) { "cancel(): cancelled both periodic and catch-up workers" }
        }
    }

    override suspend fun doWork(): Result {
        val nowSec = System.currentTimeMillis() / 1000
        Log.d(TAG) { "doWork() ENTER nowSec=$nowSec runAttempt=$runAttemptCount tags=$tags" }
        runCatching { Amethyst.instance.resourceUsage.add(UsageKeys.workerRuns("scheduledPost"), 1) }

        return try {
            val appModules = Amethyst.instance
            val store = appModules.scheduledPostStore
            val notifier = AndroidScheduledPostNotifier(applicationContext)

            val all = store.list()
            val pending = all.count { it.status == ScheduledPostStatus.PENDING }
            Log.d(TAG) { "doWork() store has ${all.size} total, $pending PENDING" }

            val dueAccounts =
                all
                    // PUBLISHING too: a claim left by a process that died is recovered by the drain.
                    .filter { (it.status == ScheduledPostStatus.PENDING || it.status == ScheduledPostStatus.PUBLISHING) && it.publishAtSec <= nowSec }
                    .mapTo(HashSet()) { it.accountPubkey }
            if (dueAccounts.isEmpty()) {
                Log.d(TAG) { "doWork() EXIT no posts due" }
                return Result.success()
            }

            // A process WorkManager started cold has no account loaded yet, and an account other
            // than the default one is loaded only by the always-on notification service: load the
            // ones that can sign, as the trust-network worker does, or nothing would go out until
            // the user opened the app.
            val accountsCache = appModules.accountsCache
            if (!accountsCache.accounts.value.keys
                    .containsAll(dueAccounts)
            ) {
                accountsCache.loadAllWritableAccounts(LocalPreferences)
            }

            // One drain per account with a due post, on that account's client and cache. The
            // shared publisher re-checks each post's author and signature before it goes out. A
            // post whose account cannot sign here stays PENDING.
            dueAccounts.forEach { pubKey ->
                val account = accountsCache.accounts.value[pubKey]
                if (account == null) {
                    Log.w(TAG) { "Account ${pubKey.take(8)} could not be loaded; its posts stay pending" }
                    return@forEach
                }
                val publisher =
                    ScheduledPostPublisher(
                        store = store,
                        client = account.client,
                        // Empty: publish to the relays stored with each post.
                        resolveRelays = { emptySet() },
                        onSent = { notifier.notifySent(it) },
                        onFailed = { post, error -> notifier.notifyFailed(post, error) },
                        consume = { event, relays, extras -> account.consumePostEvent(event, relays, extras) },
                    )
                val report = publisher.drainDue(nowSec, pubKey)
                Log.d(TAG) {
                    "doWork() ${pubKey.take(8)}: published=${report.published} failed=${report.failed} skipped=${report.skipped}"
                }
            }

            Log.d(TAG) { "doWork() EXIT success" }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "doWork() unexpected failure", e)
            Result.retry()
        }
    }
}
