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
package com.vitorpamplona.amethyst.ui.tor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.tor_progress_channel_description
import com.vitorpamplona.amethyst.commons.resources.tor_progress_channel_id
import com.vitorpamplona.amethyst.commons.resources.tor_progress_channel_name
import com.vitorpamplona.amethyst.commons.resources.tor_progress_downloading
import com.vitorpamplona.amethyst.commons.resources.tor_progress_downloading_unknown
import com.vitorpamplona.amethyst.commons.resources.tor_progress_ready
import com.vitorpamplona.amethyst.commons.resources.tor_progress_starting
import com.vitorpamplona.amethyst.commons.resources.tor_splash_connecting
import com.vitorpamplona.amethyst.commons.resources.tor_status_connected
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** What the Tor progress notification should show. Pure, so the mapping is unit-testable. */
sealed class TorProgressNotice {
    /** Opted out, or Tor is off: no notification. */
    data object Hidden : TorProgressNotice()

    /** The native client is being created; no proxy yet. */
    data object Starting : TorProgressNotice()

    /** The proxy is up and the directory is downloading. [percent] is null before Arti reports any. */
    data class Downloading(
        val percent: Int?,
    ) : TorProgressNotice()

    /** Fully bootstrapped. */
    data object Ready : TorProgressNotice()

    val isInProgress: Boolean get() = this is Starting || this is Downloading

    companion object {
        /**
         * @param permille Arti's directory-download progress, 0-1000, or negative when unknown
         *   (see [TorBackend.bootstrapProgress]).
         */
        fun from(
            enabled: Boolean,
            status: TorServiceStatus,
            permille: Int,
        ): TorProgressNotice =
            if (!enabled) {
                Hidden
            } else {
                when (status) {
                    TorServiceStatus.Off -> Hidden
                    TorServiceStatus.Connecting -> Starting
                    is TorServiceStatus.Bootstrapping -> Downloading(if (permille >= 0) (permille / 10).coerceIn(0, 100) else null)
                    is TorServiceStatus.Active -> Ready
                }
            }
    }
}

/**
 * Opt-in notification ([com.vitorpamplona.amethyst.commons.tor.TorSettings.progressNotification],
 * switched in the Tor settings) that shows the internal Tor client's startup: a progress bar with
 * the directory-download percentage, the current phase and a timer since the attempt began. When
 * Tor is ready it switches to a short "connected" notice that dismisses itself.
 *
 * It watches the backend's own [TorBackend.status], **not** [TorManager.status]: the manager's
 * flow starts Tor as soon as anything collects it, and a notification must never be the reason
 * Tor runs.
 */
class TorProgressNotifier(
    private val context: Context,
    private val scope: CoroutineScope,
    private val enabled: StateFlow<Boolean>,
    private val status: StateFlow<TorServiceStatus>,
    private val bootstrapProgress: StateFlow<Int>,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var channelCreated = false

    /** Start of the current connecting span, for the notification's timer. 0 when not connecting. */
    private var attemptStartedAtMs = 0L

    fun start() {
        scope.launch {
            combine(enabled, status, bootstrapProgress) { on, s, p -> TorProgressNotice.from(on, s, p) }
                .distinctUntilChanged()
                .collect { show(it) }
        }
    }

    private suspend fun show(notice: TorProgressNotice) {
        val nm = NotificationManagerCompat.from(context)

        if (notice.isInProgress) {
            if (attemptStartedAtMs == 0L) attemptStartedAtMs = nowMs()
        } else {
            attemptStartedAtMs = 0L
        }

        if (notice == TorProgressNotice.Hidden) {
            nm.cancel(NOTIFICATION_ID)
            return
        }

        // POST_NOTIFICATIONS is runtime-granted on Android 13+.
        if (!nm.areNotificationsEnabled()) return
        ensureChannel()

        val builder =
            NotificationCompat
                .Builder(context, loadStringRes(Res.string.tor_progress_channel_id))
                .setSmallIcon(R.drawable.amethyst)
                .setContentIntent(openAppIntent())
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setLocalOnly(true)

        when (notice) {
            TorProgressNotice.Starting -> {
                builder
                    .setContentTitle(loadStringRes(Res.string.tor_splash_connecting))
                    .setContentText(loadStringRes(Res.string.tor_progress_starting))
                    .setProgress(0, 0, true)
                    .inProgress()
            }

            is TorProgressNotice.Downloading -> {
                val percent = notice.percent
                builder.setContentTitle(loadStringRes(Res.string.tor_splash_connecting))
                if (percent != null) {
                    builder
                        .setContentText(loadStringRes(Res.string.tor_progress_downloading, percent))
                        .setSubText("$percent%")
                        .setProgress(100, percent, false)
                } else {
                    builder
                        .setContentText(loadStringRes(Res.string.tor_progress_downloading_unknown))
                        .setProgress(0, 0, true)
                }
                builder.inProgress()
            }

            TorProgressNotice.Ready -> {
                builder
                    .setContentTitle(loadStringRes(Res.string.tor_status_connected))
                    .setContentText(loadStringRes(Res.string.tor_progress_ready))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setTimeoutAfter(READY_VISIBLE_MS)
            }

            TorProgressNotice.Hidden -> return
        }

        try {
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Race: permission revoked between the check above and notify().
            Log.w(TAG, "Could not post the Tor progress notification", e)
        }
    }

    private fun NotificationCompat.Builder.inProgress(): NotificationCompat.Builder =
        setOngoing(true)
            .setShowWhen(true)
            .setWhen(attemptStartedAtMs)
            .setUsesChronometer(true)

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private suspend fun ensureChannel() {
        if (channelCreated) return
        val channel =
            NotificationChannel(
                loadStringRes(Res.string.tor_progress_channel_id),
                loadStringRes(Res.string.tor_progress_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = loadStringRes(Res.string.tor_progress_channel_description)
                setShowBadge(false)
            }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        channelCreated = true
    }

    companion object {
        private const val TAG = "TorProgressNotifier"
        private const val NOTIFICATION_ID = 0x7042
        private const val READY_VISIBLE_MS = 5_000L
    }
}
