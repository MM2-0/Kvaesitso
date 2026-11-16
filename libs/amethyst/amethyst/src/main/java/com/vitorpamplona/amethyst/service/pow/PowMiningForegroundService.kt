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
package com.vitorpamplona.amethyst.service.pow

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.PowerManager
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.service.pow.PoWEstimator
import com.vitorpamplona.amethyst.commons.service.pow.PoWEventShape
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobPhase
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobState
import com.vitorpamplona.amethyst.commons.service.pow.PoWRateEstimate
import com.vitorpamplona.amethyst.commons.service.pow.platformMinerWorkers
import com.vitorpamplona.amethyst.service.foreground.FlowProgressForegroundService
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Short-lived foreground service that shields the PoW mining queue from the
 * cached-apps freezer: while it runs the process stays schedulable, so posts
 * finish mining even after the user backgrounds the app.
 *
 * Uses the Android 14+ `shortService` type — no special permission, but a
 * hard ~3 minute budget. On `onTimeout` the service exits cleanly; every
 * persistable job is already checkpointed by [com.vitorpamplona.amethyst.commons.service.pow.PoWJobStore], so anything still
 * unmined resumes on the next app launch. Started on every enqueue (the app
 * is necessarily in the foreground then), stops itself when the queue drains.
 *
 * Staying schedulable is not enough on its own once the user also locks the
 * screen or leaves the app, so while it runs the service also:
 * - holds a partial wake lock, or the CPU suspends with the screen off and the
 *   nonce search stalls until the phone is unlocked;
 * - keeps the relay pool (and Tor) up. The UI stops holding them ~30 s after
 *   the app is backgrounded, so a post mined after that would only reach the
 *   in-memory outbox — while its checkpoint and draft were already deleted —
 *   and die with the process;
 * - lingers [PUBLISH_GRACE_MS] after the queue drains, so the event that was
 *   just handed to the pool actually leaves before the process is frozen.
 *
 * The notification card (a live [androidx.core.app.NotificationCompat.ProgressStyle]) and all the
 * service lifecycle live in [FlowProgressForegroundService]; this subclass only maps mining state
 * to that card.
 */
class PowMiningForegroundService : FlowProgressForegroundService<ImmutableList<PoWJobState>>() {
    // Only passed to startForeground on API 34+, where the type exists.
    @Suppress("InlinedApi")
    override val fgsType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
    override val channelId = CHANNEL_ID
    override val channelNameRes = R.string.pow_notification_channel_name
    override val channelDescRes = R.string.pow_notification_channel_description
    override val notificationId = NOTIFICATION_ID
    override val cancelAction = ACTION_CANCEL_ALL
    override val cancelLabelRes = R.string.pow_notification_cancel_all
    override val secondaryAction = ACTION_SEND_ALL_NOW
    override val secondaryLabelRes = R.string.pow_notification_send_without_pow

    // clock-driven refresh for the time-left text and bar; the shortService budget (~3 min)
    // caps this at a handful of updates.
    override val refreshMs: Long = PROGRESS_REFRESH_MS

    // Session totals so the progress track can show "done / enqueued since the service
    // started" — the queue itself only knows what is still pending.
    private var sessionTotal = 0
    private var lastQueueSize = 0

    // Whether the last non-empty queue still had a job publishing: a queue that drains by
    // publishing needs the grace for the event to leave; one drained by cancel does not.
    private var drainedByPublishing = false

    override fun stopGraceMs() = if (drainedByPublishing) PUBLISH_GRACE_MS else 0L

    override fun renderDraining() = Content(stringRes(this, R.string.pow_notification_sending), null, Bar.Indeterminate)

    // Benchmarked once per service run (~250 ms, cached by the estimator).
    @Volatile
    private var hashRate: PoWRateEstimate? = null

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        running = true

        wakeLock =
            runCatching {
                (getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "amethyst:pow-mining")
                    .apply {
                        setReferenceCounted(false)
                        acquire(WAKE_LOCK_TIMEOUT_MS)
                    }
            }.onFailure { Log.w(TAG, "Could not acquire the mining wake lock", it) }
                .getOrNull()

        // Re-arm the timeout while the service lives: before Android 14 there is no FGS budget
        // and a hard nonce can mine for hours, but a lock with no timeout could outlive a
        // service that died without onDestroy.
        scope.launch {
            while (true) {
                delay(WAKE_LOCK_TIMEOUT_MS / 2)
                wakeLock?.let { if (it.isHeld) it.acquire(WAKE_LOCK_TIMEOUT_MS) }
            }
        }

        // Mirrors what the resumed UI collects (AndroidAppRoot's ManageRelayServices +
        // ManageWebOkHttp): subscribed, they keep the relay pool connected and, through
        // the connector's combine and the proxy-port provider, Tor up.
        val app = Amethyst.instance
        scope.launch(Dispatchers.IO) {
            launch { app.relayProxyClientConnector.relayServices.collect {} }
            launch { app.okHttpClients.defaultHttpClient.collect {} }
            launch { app.okHttpClients.defaultHttpClientWithoutProxy.collect {} }
        }
    }

    override fun onDestroy() {
        running = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun state() = Amethyst.instance.powPublishQueue.jobs

    override fun isActive(value: ImmutableList<PoWJobState>) = value.isNotEmpty()

    override fun cancelAll() = Amethyst.instance.powPublishQueue.cancelAll()

    override fun onSecondaryAction() = Amethyst.instance.powPublishQueue.sendAllWithoutPow()

    override fun needsClockRefresh(value: ImmutableList<PoWJobState>) = value.any { it.isMining }

    override fun onStarted() {
        // The queue's own worker count, measured live once its miners run: a one-worker benchmark
        // made the notification promise several times the real wait.
        scope.launch { hashRate = PoWEstimator.currentRate(platformMinerWorkers()) }
    }

    override fun onEmission(value: ImmutableList<PoWJobState>) {
        if (value.isNotEmpty()) drainedByPublishing = value.any { it.phase == PoWJobPhase.PUBLISHING }
        if (value.size > lastQueueSize) sessionTotal += value.size - lastQueueSize
        lastQueueSize = value.size
    }

    private fun miningShape(value: ImmutableList<PoWJobState>) = value.firstOrNull { it.isMining }?.shape ?: PoWEventShape.SHORT_NOTE

    override fun render(value: ImmutableList<PoWJobState>): Content {
        val done = (sessionTotal - value.size).coerceAtLeast(0)
        val total = (done + value.size).coerceAtLeast(1)

        val current = value.firstOrNull { it.isMining } ?: value.firstOrNull()

        // expected duration for the job being mined right now, so the card can say
        // "≈ 10 minutes left" and fill its bar toward a predictable end.
        val rate = hashRate
        val startedAt = current?.miningStartedAt
        val expectedSec =
            if (current != null && rate != null) {
                PoWEstimator.estimateSeconds(current.difficulty, rate.hashesPerSecond(current.shape ?: PoWEventShape.SHORT_NOTE, miningShape(value)))
            } else {
                null
            }
        val elapsedSec = startedAt?.let { (TimeUtils.now() - it).coerceAtLeast(0) }

        val base =
            current?.let {
                pluralStringRes(this, R.plurals.pow_mining_job, it.difficulty, stringRes(this, powKindLabelResId(it.kind)), it.difficulty)
            } ?: stringRes(this, R.string.pow_mining_title)
        val text =
            if (expectedSec != null && elapsedSec != null) {
                "$base • ${formatTimeLeft(this, expectedSec, elapsedSec)}"
            } else {
                base
            }

        val fraction = if (expectedSec != null && elapsedSec != null) elapsedSec / expectedSec else null

        val title =
            if (value.size > 1) {
                pluralStringRes(this, R.plurals.pow_mining_progress, value.size, value.size)
            } else {
                stringRes(this, R.string.pow_mining_title)
            }

        val bar =
            if (total <= 1) {
                // single post: fill toward the estimated duration; past the mean the search is
                // memoryless, so sweep instead of lying.
                if (fraction != null && fraction < 1.0) Bar.Determinate(fraction) else Bar.Indeterminate
            } else {
                Bar.Segmented(total, done)
            }

        return Content(title, text, bar)
    }

    companion object {
        private const val TAG = "PowMiningFgs"
        private const val CHANNEL_ID = "pow_mining"
        private const val NOTIFICATION_ID = 0x504F57 // "POW"
        private const val ACTION_CANCEL_ALL = "com.vitorpamplona.amethyst.pow.CANCEL_ALL"
        private const val ACTION_SEND_ALL_NOW = "com.vitorpamplona.amethyst.pow.SEND_ALL_NOW"

        private const val PROGRESS_REFRESH_MS = 30_000L

        // Time for a just-mined post to connect, send and get its OK before the service
        // lets go of the relays and the process becomes freezable.
        private const val PUBLISH_GRACE_MS = 20_000L

        // Safety net only: onDestroy releases it, and the service re-arms it while alive.
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        // Best-effort de-dup for start(): the queue calls it on EVERY enqueue.
        @Volatile
        private var running = false

        /**
         * Best-effort start: enqueue happens while the user is interacting with the app,
         * so the foreground-start allowance normally holds.
         */
        fun start(context: Context) {
            if (running) return
            FlowProgressForegroundService.start(context, PowMiningForegroundService::class.java, TAG)
        }
    }
}
