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
package com.vitorpamplona.amethyst.service.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vitorpamplona.quartz.utils.Log

/**
 * Receives a broadcast from NotificationRelayService.onDestroy() and
 * enqueues a one-time WorkManager task to restart the service.
 *
 * This catches kills that START_STICKY might miss (e.g., OEM battery
 * optimizers, aggressive memory reclaim). The WorkManager task requires
 * network connectivity, so the service won't restart until the device
 * has an active connection.
 *
 * Pattern inspired by ntfy's AutoRestartReceiver.
 */
class AutoRestartReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AutoRestartReceiver"
        private const val WORK_NAME = "notification_service_restart"
    }

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != NotificationRelayService.ACTION_AUTO_RESTART) return

        Log.d(TAG, "Received auto-restart broadcast, enqueuing restart work")

        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            OneTimeWorkRequestBuilder<NotificationCatchUpWorker>()
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
