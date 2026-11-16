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

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * A foreground service that maintains persistent WebSocket connections to the user's
 * inbox relays for real-time notification delivery.
 *
 * This service keeps the shared NostrClient alive by collecting the relayServices flow.
 * It does NOT create its own relay subscriptions — instead, it relies on the
 * AccountFilterAssembler subscription from the Compose UI tree, which stays active
 * as long as the Activity is alive (even when stopped/backgrounded). That subscription
 * covers notifications, gift wraps, metadata, follows, relay lists, and drafts.
 *
 * Heavy feed subscriptions (Home, Video, Discovery, ChatroomList) are managed
 * separately with lifecycle awareness — they pause when the app backgrounds
 * and resume when it foregrounds. This prevents bandwidth waste on feeds
 * nobody is viewing.
 *
 * Auto-restart mechanisms (inspired by ntfy):
 * - START_STICKY: Android restarts killed services
 * - onTaskRemoved(): 1-second alarm restart when swiped from recents
 * - onDestroy(): broadcast to AutoRestartReceiver for WorkManager restart
 * - AlarmManager watchdog (external, ServiceWatchdogManager)
 * - WorkManager catch-up (external, NotificationCatchUpWorker)
 * - BOOT_COMPLETED + MY_PACKAGE_REPLACED receivers (external, BootCompletedReceiver)
 */
class NotificationRelayService : Service() {
    companion object {
        private const val TAG = "NotificationRelayService"
        private const val CHANNEL_ID = "notification_relay_service"

        /** Parsed back into `Route.ActiveSubscriptions` by `MainActivity.uriToRoute`. */
        private const val ACTIVE_SUBSCRIPTIONS_URI = "activesubs"

        private const val NOTIFICATION_ID = 9832

        private const val ACTION_START = "com.vitorpamplona.amethyst.START_NOTIFICATION_SERVICE"

        // Throttle interval for refreshing the persistent notification's relay count.
        // Keeps notification updates well under Android's rate limit (~10/s).
        private const val NOTIFICATION_REFRESH_MS = 1000L

        // Toggles the expanded per-job breakdown on and off. Fired by the notification's own
        // action button, so the details are always something the user asked for.
        private const val ACTION_SHOW_DETAILS = "com.vitorpamplona.amethyst.SHOW_NOTIFICATION_SERVICE_DETAILS"
        private const val ACTION_HIDE_DETAILS = "com.vitorpamplona.amethyst.HIDE_NOTIFICATION_SERVICE_DETAILS"

        const val ACTION_AUTO_RESTART = "com.vitorpamplona.amethyst.AUTO_RESTART_NOTIFICATION_SERVICE"

        /**
         * True while this process's instance is promoted to the foreground. Process-local on
         * purpose: a fresh process starts with no service, so false is the right default.
         */
        @Volatile
        private var isForeground = false

        fun start(context: Context) {
            // Every startForegroundService() arms a 10s "must call startForeground()" deadline
            // that only the main thread can meet, in onStartCommand. The restart layers call
            // this on every resume, worker run and watchdog tick, so a running service would
            // keep re-arming it, and a main thread busy for 10s (cold-start ingest on a slow
            // device) crashed the app with ForegroundServiceDidNotStartInTimeException.
            if (isForeground) return

            val intent =
                Intent(context, NotificationRelayService::class.java).apply {
                    action = ACTION_START
                }

            // While the app is on screen a plain startService() is allowed and carries no
            // deadline; onCreate still promotes the service with startForeground().
            if (isAppInForeground()) {
                try {
                    context.startService(intent)
                    return
                } catch (e: IllegalStateException) {
                    // Left the foreground between the check and the call: fall through.
                    Log.w(TAG, "Plain service start refused, using a foreground start", e)
                }
            }

            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e is ForegroundServiceStartNotAllowedException
                ) {
                    // Android 12+ blocks startForegroundService() from the background unless
                    // the caller has a temporary FGS exemption (e.g. broadcast receiver,
                    // exact alarm, high-priority FCM). Cold-start triggered by a flow emission
                    // from AlwaysOnNotificationServiceManager hits this path. The other
                    // notification layers (BootCompletedReceiver, ServiceWatchdogManager,
                    // NotificationCatchUpWorker) plus MainActivity.onResume retry from
                    // contexts that are allowed.
                    Log.w(TAG) { "Foreground service start not allowed from background; will retry from another layer" }
                } else {
                    Log.e(TAG, "Failed to start foreground service", e)
                }
            }
        }

        private fun isAppInForeground(): Boolean {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }

        fun stop(context: Context) {
            // Cleared now, not in onDestroy: a start() right after a stop() must not be skipped.
            isForeground = false
            context.stopService(Intent(context, NotificationRelayService::class.java))
        }

        // Gate for the restart layers (watchdog, boot, catch-up worker, onResume): the
        // global master must be on, and — matching the manager's rule — at least one loaded
        // writable account must be participating. An empty cache (logged out, or accounts
        // not yet loaded) gates OFF, so the layers never resurrect the service for a
        // logged-out user; on cold boot the account loads headlessly and the manager starts
        // the service itself once a participant exists.
        fun isEnabled(context: Context): Boolean =
            try {
                // The service also backs the NIP-46 signer, so an account with the signer on
                // participates just like one with always-on notifications on.
                LocalPreferences.isNotificationServiceEnabled() &&
                    Amethyst.instance.accountsCache.accounts.value.values.any {
                        it.settings.alwaysOnNotificationService.value || it.settings.nip46SignerEnabled.value
                    }
            } catch (e: Exception) {
                false
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var relayServiceCollectorJob: Job? = null
    private var connectedRelayCount = 0

    /** Last non-empty per-job breakdown, kept so a reconnect does not blank the expanded view. */
    private var lastBreakdown: List<String> = emptyList()

    /** Whether the user asked for the per-job breakdown. Off until they tap "show details". */
    private var detailsExpanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        Amethyst.instance.alwaysOnSession.setActive(true)
        createNotificationChannel()
        ensureForeground()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Log.d(TAG, "Starting service")
        // The details toggle re-enters here through the notification's action button. It only
        // flips the flag; the rebuild happens in the ensureForeground() below, which reposts the
        // notification with (or without) the breakdown.
        when (intent?.action) {
            ACTION_SHOW_DETAILS -> detailsExpanded = true
            ACTION_HIDE_DETAILS -> detailsExpanded = false
        }
        // Every startForegroundService() call re-arms Android's "must call
        // startForeground() within the timeout" requirement — including the repeated
        // calls MainActivity.onResume fires on each resume, even when the service is
        // already running and already foregrounded. We therefore call startForeground()
        // on EVERY start command, not just the first. Skipping it on later starts (the
        // old `if (foregroundStarted) return` guard) left the re-armed requirement
        // unsatisfied and triggered ForegroundServiceDidNotStartInTimeException, which
        // crashes the whole app. startForeground() is idempotent — repeated calls just
        // refresh the existing notification.
        if (!ensureForeground()) return START_NOT_STICKY
        startRelayConnection()
        return START_STICKY
    }

    /**
     * Called when the user swipes the app from recents. Some OEMs kill the
     * foreground service at this point. Schedule an alarm to restart in 1 second.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!isEnabled(this)) return

        Log.d(TAG, "Task removed, scheduling restart alarm")
        val restartIntent =
            Intent(this, NotificationRelayService::class.java).apply {
                action = ACTION_START
            }
        val pendingIntent =
            PendingIntent.getService(
                this,
                2,
                restartIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
            )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent,
        )
    }

    /**
     * Called when the service is being destroyed. Send a broadcast to
     * AutoRestartReceiver which will enqueue a WorkManager task to restart.
     * This catches kills that START_STICKY might miss.
     */
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        isForeground = false
        Amethyst.instance.alwaysOnSession.setActive(false)
        relayServiceCollectorJob?.cancel()
        scope.cancel()

        if (isEnabled(this)) {
            Log.d(TAG, "Broadcasting auto-restart request")
            val intent =
                Intent(this, AutoRestartReceiver::class.java).apply {
                    action = ACTION_AUTO_RESTART
                }
            sendBroadcast(intent)
        }

        super.onDestroy()
    }

    /**
     * Promotes the service to the foreground. Called on creation AND on every
     * [onStartCommand], because each [Context.startForegroundService] re-arms the
     * "must call startForeground() within the timeout" requirement and the old code's
     * early-return-when-already-started skipped it, crashing the app with
     * ForegroundServiceDidNotStartInTimeException. Calling startForeground() repeatedly
     * is safe — it just refreshes the existing notification.
     *
     * Returns true if the service is foregrounded, false if promotion failed (in which
     * case the service has already been told to stop).
     */
    private fun ensureForeground(): Boolean {
        try {
            val notification = buildNotification(connectedRelayCount)
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
            isForeground = true
            return true
        } catch (e: Exception) {
            isForeground = false
            // Any failure to promote to the foreground leaves the service in a
            // "started but not foregrounded" zombie state. Once startForegroundService()
            // has been called, Android expects startForeground() within the timeout; if it
            // never lands it fires ForegroundServiceDidNotStartInTimeException and crashes
            // the whole app. Tearing the service down clears the OS's fgRequired flag,
            // which cancels that pending timeout, so we stopSelf() on EVERY failure path —
            // not just ForegroundServiceStartNotAllowedException. Other causes include
            // OEM-specific RemoteException/IllegalStateException and resource lookup
            // failures while building the notification.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                Log.w(TAG, "Foreground service start not allowed, stopping self")
            } else {
                Log.e(TAG, "Failed to start foreground, stopping self", e)
            }
            stopSelf()
            return false
        }
    }

    /**
     * Keeps the relay infrastructure alive. The service collects two flows:
     *
     * 1. relayServices: Keeps the RelayProxyClientConnector active (connectivity,
     *    Tor, network changes). Without this, the client disconnects 30s after
     *    the UI stops collecting.
     *
     * 2. connectedRelaysFlow: Updates the persistent notification with relay count.
     *
     * The service does NOT create its own relay subscriptions. Instead, it relies on
     * the AccountFilterAssembler subscription that lives in the Compose tree (LoggedInPage).
     * That subscription stays active as long as the Activity exists (even when stopped)
     * and covers: metadata, follows, notifications (inbox relays), gift wraps (DM relays),
     * drafts, and relay list changes. Since the service keeps the client connected,
     * those subscriptions remain active on the relays.
     */
    @OptIn(FlowPreview::class)
    private fun startRelayConnection() {
        relayServiceCollectorJob?.cancel()
        relayServiceCollectorJob =
            scope.launch {
                launch {
                    Amethyst.instance.relayProxyClientConnector.relayServices.collectLatest {
                        Log.d(TAG) { "Relay services state updated: $it" }
                    }
                }

                launch {
                    // This flow used to over-report: it is fed by socket callbacks, and until
                    // the OkHttp adapters answered a relay's CLOSE frame a relay-initiated close
                    // produced none (no onClosed, no onFailure, and a silent cancel()
                    // afterwards), so after the feeds tore down in the background it carried
                    // hundreds of already-dropped relays for minutes. The pool now clears it
                    // itself whenever it lets a relay go, and every transport reports its
                    // session end exactly once (see WebSocket), so what it emits is the count.
                    //
                    // sample() caps how often we touch the notification. During feed
                    // load/teardown connectedRelaysFlow churns dozens of times per second;
                    // posting on every delta blows past Android's notification rate limit
                    // (~10/s), which silently drops updates and leaves the visible count
                    // stuck on a stale intermediate value. One refresh per second stays
                    // well under the limit and always lands the settled count.
                    Amethyst.instance.client
                        .connectedRelaysFlow()
                        .sample(NOTIFICATION_REFRESH_MS)
                        .collectLatest { relays ->
                            val count = relays.size
                            if (count != connectedRelayCount) {
                                connectedRelayCount = count
                                updateNotification(count)
                            }
                        }
                }
            }
    }

    private fun updateNotification(connectedRelays: Int) {
        postNotification(connectedRelays)
        // The breakdown names each job from the shared string catalog, whose only
        // non-composable accessor suspends - so it is recomputed off this path and
        // the card reposted once the text actually changes.
        if (detailsExpanded) {
            scope.launch {
                val fresh = RelayPurposeSummary.lines()
                if (fresh.isNotEmpty() && fresh != lastBreakdown) {
                    lastBreakdown = fresh
                    postNotification(connectedRelays)
                }
            }
        }
    }

    private fun postNotification(connectedRelays: Int) {
        val notification = buildNotification(connectedRelays)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(connectedRelays: Int): Notification {
        val contentText =
            if (connectedRelays <= 0) {
                getString(R.string.always_on_notif_connecting)
            } else {
                // Every connected relay, in both states. Backgrounded used to say "inbox relays",
                // but the count never was just the inbox: DM, wallet, nutzap, group, signer and
                // account relays all stay connected too, so it contradicted its own breakdown.
                pluralStringRes(this, R.plurals.always_on_notif_connected_foreground, connectedRelays, connectedRelays)
            }

        // Tapping goes to the screen that answers the question the notification raises — "why is it
        // connected to N relays" — rather than to whatever tab was last open.
        val openAppIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = ACTIVE_SUBSCRIPTIONS_URI.toUri()
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        // Opt-in, never automatic. Android auto-expands a notification when it is the only one in
        // the shade, and there is no way to opt out of that — so attaching the breakdown as a
        // BigTextStyle up front made the per-job list the *default* view for anyone whose shade was
        // otherwise empty, which is the opposite of what it is for. The card is therefore built
        // with no expanded style at all until someone taps "show details"; that reposts it with the
        // breakdown and a "hide details" action that puts it back to the bare count.
        //
        // Held across reconnects rather than recomputed blindly: the breakdown is derived from the
        // *connected* relays, so a drop to zero (the "connecting…" state) would otherwise empty it and
        // the expanded view would collapse to a single line exactly when someone is most likely
        // looking at it. What each connection is *for* does not change while it is re-establishing,
        // so the last known answer is still the right one; only the count above it goes stale, and
        // that count is already labelled "connecting".
        //
        // Computed only while expanded: walking every connected relay's active requests once a
        // second is wasted work when nobody has asked to see the result.
        val breakdown = if (detailsExpanded) lastBreakdown.takeIf { it.isNotEmpty() } else null

        val detailsIntent =
            Intent(this, NotificationRelayService::class.java).apply {
                action = if (detailsExpanded) ACTION_HIDE_DETAILS else ACTION_SHOW_DETAILS
            }
        val detailsPendingIntent =
            PendingIntent.getService(
                this,
                if (detailsExpanded) 4 else 3,
                detailsIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val detailsLabel =
            getString(
                if (detailsExpanded) {
                    R.string.always_on_notif_hide_details
                } else {
                    R.string.always_on_notif_show_details
                },
            )

        // Deliberately left ungrouped. This notification is ongoing and IMPORTANCE_LOW, so it
        // sits in the shade's Silent section next to the low-importance content kinds
        // (reactions, reposts) — and Android 16 sweeps everything ungrouped in a section into
        // one aggregate bundle whose summary inherits FLAG_ONGOING_EVENT from any child that
        // has it, making the whole bundle un-swipeable. Giving this one a group of its own
        // would not help: a group with a summary but no children, or a child with no summary,
        // is force-grouped just the same. What keeps content notifications out of that bundle
        // is that they always post their own group summary (see NotificationUtils), which
        // leaves this the only ungrouped silent notification we post — one is below the
        // threshold, so no bundle is formed and nothing gets stapled to it.
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.always_on_notif_title))
            .setContentText(contentText)
            .apply {
                breakdown?.let {
                    setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .setBigContentTitle(getString(R.string.always_on_notif_title))
                            .bigText(contentText + "\n\n" + it.joinToString("\n")),
                    )
                }
            }.addAction(0, detailsLabel, detailsPendingIntent)
            .setSmallIcon(R.drawable.amethyst_service)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.always_on_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.always_on_notif_channel_description)
                setShowBadge(false)
            }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
