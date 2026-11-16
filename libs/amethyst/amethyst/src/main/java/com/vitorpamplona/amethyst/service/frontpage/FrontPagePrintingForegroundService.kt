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
import android.content.pm.ServiceInfo
import android.os.PowerManager
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrintHost
import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrintJob
import com.vitorpamplona.amethyst.commons.frontpage.PrintJobStage
import com.vitorpamplona.amethyst.service.foreground.FlowProgressForegroundService
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps a print the reader started alive after they leave the app: a manual print is a coroutine
 * on the account's scope, so without this a backgrounded app is frozen by the cached-apps freezer,
 * loses its network to Doze, or is killed mid-print, possibly after the model call was paid for.
 * The ongoing card shows the step and how long it has run, with Cancel ([FrontPagePrinters.cancelManualPrints]).
 * When the print ends the service stops and removes its card; the result is the printer's own
 * notification ([AndroidFrontPageNotifier]: ready, or why it failed), posted when the reader isn't
 * on the front page.
 *
 * `dataSync`, not PoW's `shortService`: a print reads the posts, streams the page from the AI and
 * checks it, which can run past shortService's ~3 minute budget. dataSync has no limit of its own
 * on Android 14; Android 15 caps it at 6 hours in 24 (then [onTimeout] stops it), far more than a
 * print takes. It must start while the app is in the foreground, which a manual print always is:
 * the reader just pressed Print. Needs `FOREGROUND_SERVICE_DATA_SYNC` (declared for Blossom sync).
 *
 * Like the mining service it holds a partial wake lock and keeps the relay pool and the HTTP
 * clients up while it runs, since the UI lets go of them shortly after the app is backgrounded.
 */
class FrontPagePrintingForegroundService : FlowProgressForegroundService<Map<HexKey, FrontPagePrintJob>>() {
    // Only passed to startForeground on API 34+, where the type exists.
    @Suppress("InlinedApi")
    override val fgsType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    override val channelId = CHANNEL_ID
    override val channelNameRes = R.string.front_page_print_channel_name
    override val channelDescRes = R.string.front_page_print_channel_description
    override val notificationId = NOTIFICATION_ID
    override val cancelAction = ACTION_CANCEL
    override val cancelLabelRes = R.string.front_page_print_cancel

    // No clock in the text: the notification's chronometer counts up on its own, so the card is
    // re-sent only when the step changes (the jobs' emissions), never once a second.

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        wakeLock =
            runCatching {
                (getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "amethyst:front-page-print")
                    .apply {
                        setReferenceCounted(false)
                        acquire(WAKE_LOCK_TIMEOUT_MS)
                    }
            }.onFailure { Log.w(TAG, "Could not acquire the printing wake lock", it) }
                .getOrNull()

        // As the mining service does: subscribed, these keep the relay pool, Tor and the HTTP
        // clients up after the UI stops holding them.
        val app = Amethyst.instance
        scope.launch(Dispatchers.IO) {
            launch { app.relayProxyClientConnector.relayServices.collect {} }
            launch { app.okHttpClients.defaultHttpClient.collect {} }
            launch { app.okHttpClients.defaultHttpClientWithoutProxy.collect {} }
        }
        scope.launch {
            while (true) {
                delay(WAKE_LOCK_TIMEOUT_MS / 2)
                wakeLock?.let { if (it.isHeld) it.acquire(WAKE_LOCK_TIMEOUT_MS) }
            }
        }
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun state() = Amethyst.instance.frontPagePrinters.printing

    override fun isActive(value: Map<HexKey, FrontPagePrintJob>) = value.values.any { it.runningManual }

    override fun cancelAll() = Amethyst.instance.frontPagePrinters.cancelManualPrints()

    /**
     * Android 15 ended dataSync's budget: the print can't go on unprotected (frozen, or killed
     * after the model was paid), so it is cancelled like the reader's Cancel, and ends as one.
     */
    override fun onBudgetExhausted() = Amethyst.instance.frontPagePrinters.cancelManualPrints()

    override fun render(value: Map<HexKey, FrontPagePrintJob>): Content {
        val job = value.values.firstOrNull { it.runningManual } ?: return Content(stringRes(this, R.string.front_page_print_title), null, Bar.Indeterminate)
        val step =
            when (val stage = job.stage) {
                PrintJobStage.Probe -> stringRes(this, R.string.front_page_print_step_probe)
                is PrintJobStage.Reading -> stringRes(this, R.string.front_page_print_step_pull, stage.done, stage.total)
                is PrintJobStage.Writing -> stringRes(this, if (stage.round > 1) R.string.front_page_print_step_fix else R.string.front_page_print_step_write)
                PrintJobStage.Checking -> stringRes(this, R.string.front_page_print_step_check)
                PrintJobStage.Publishing -> stringRes(this, R.string.front_page_print_step_publish)
            }
        val stage = job.stage
        val bar = if (stage is PrintJobStage.Reading && stage.total > 0) Bar.Segmented(stage.total, stage.done) else Bar.Indeterminate
        return Content(stringRes(this, R.string.front_page_print_title), step, bar, chronometerSince = job.startedAt * 1000)
    }

    companion object {
        private const val TAG = "FrontPagePrintFgs"
        private const val CHANNEL_ID = "front_page_printing"
        private const val NOTIFICATION_ID = 0x4F4253 // "OBS"
        private const val ACTION_CANCEL = "com.vitorpamplona.amethyst.frontpage.PRINT_CANCEL"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        fun start(context: Context) {
            FlowProgressForegroundService.start(context, FrontPagePrintingForegroundService::class.java, TAG)
        }
    }
}

/**
 * The shared printer's port on Android: a manual print starts the foreground service, which
 * watches [FrontPagePrinters.printing] and stops itself when no manual print is left running, so
 * the end needs nothing here.
 */
class AndroidFrontPagePrintHost(
    private val context: Context,
) : FrontPagePrintHost {
    override fun printStarted(account: HexKey) = FrontPagePrintingForegroundService.start(context)

    override fun printEnded(account: HexKey) {
        // The service sees the job end in the printers' state and stops on its own.
    }
}
