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
package com.vitorpamplona.amethyst.service.wot

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

/**
 * Keeps every loaded account's Web of Trust network index current while the app is not in use:
 * once a day, on an unmetered network with enough battery, it runs whatever sync is due (the
 * weekly negentropy full check, a small update, or a pending first download).
 *
 * Opening the app does the same for the active account (see AppModules), so this is the
 * safety net for accounts that only receive push notifications.
 */
class TrustNetworkSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        // A cold process has no accounts loaded yet: load the ones that can sign, like the
        // always-on notification service does.
        val cache = Amethyst.instance.accountsCache
        cache.loadAllWritableAccounts(LocalPreferences)
        val accounts = cache.accounts.value.values
        // The provider lists resolve together (each can wait up to 10s); the syncs then run one at
        // a time, since a download holds tens of MB. Each waits for the sync it starts (or the
        // one already running), so the job does not end, and let the process die, mid-sync.
        coroutineScope { accounts.map { async { it.trustNetwork.awaitReady() } }.awaitAll() }
        val runs = accounts.map { it.trustNetwork.syncDue() }
        Log.d(TAG) { "Checked the trust network of ${accounts.size} accounts: ${runs.map { it?.outcome?.let { o -> o::class.simpleName } }}" }
        return Result.success()
    }

    companion object {
        private const val TAG = "TrustNetworkSyncWorker"
        private const val WORK_NAME = "trust_network_sync"

        fun schedule(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<TrustNetworkSyncWorker>(1, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
