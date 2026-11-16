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

import android.content.Context
import android.os.PowerManager
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.auth.ScreenAuthAccount
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderQueryState
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Makes a tray notification *observable*: it renders immediately from whatever
 * is already in [com.vitorpamplona.amethyst.commons.model.cache.LocalCache], then — if the
 * involved users' names/pictures or the involved notes' content/media haven't
 * loaded yet — opens a bounded relay window, subscribes to those users and
 * notes, and re-renders the same notification (replacing it in place) as the
 * missing data arrives.
 *
 * This is the generalized form of the ad-hoc subscribe-and-wait that
 * `notifyIncomingCall` and `wakeUpFor` do: on a cold push the process has no
 * cached metadata, so without this a notification would show a raw pubkey and
 * no avatar. With it, the notification fills in the moment the kind:0 (and the
 * post itself, for media) lands.
 *
 * The re-render path relies on notifications being keyed by a stable id and on
 * `setOnlyAlertOnce(true)` (see [PushNotifier]) so replacements update silently
 * instead of re-buzzing.
 */
object NotificationEnricher {
    private const val TAG = "NotificationEnricher"
    private const val WINDOW_MS = 25_000L

    /** Collapses the StateFlow initial-value burst and rapid relay arrivals into
     * one re-render, instead of rebuilding (reloading avatars, re-posting) on
     * every emission. */
    private const val RENDER_DEBOUNCE_MS = 250L

    /** Caps concurrent enrichment windows so a push burst can't hold N
     * simultaneous relay windows + wakelocks + subscriptions. */
    private const val MAX_CONCURRENT_WINDOWS = 4
    private val windowLimiter = Semaphore(MAX_CONCURRENT_WINDOWS)

    /**
     * Posts [build] now, then observes [users] and [notes] for the enrichment
     * window, re-running [build] whenever their metadata/content changes, until
     * [isComplete] reports everything needed is present or the window elapses.
     *
     * Non-blocking: the observation runs detached on the app IO scope under its
     * own wakelock, so the caller (the notification dispatcher) is never held up.
     * When [isComplete] is already satisfied, no relay window is opened.
     */
    fun enrichAndPost(
        context: Context,
        account: Account,
        notificationId: String,
        users: Collection<User>,
        notes: Collection<Note>,
        isComplete: () -> Boolean,
        build: suspend () -> Unit,
    ) {
        Amethyst.instance.applicationIOScope.launch {
            withEnrichmentWakeLock(context) {
                // 1. Immediate render from cache — under the wakelock so a
                //    cold-push post can't be lost to Doze before it reaches the
                //    tray (the parent dispatcher wakelock is already released).
                runBuild(build)

                // 2. If we already have everything, we're done — no relay window.
                if (isComplete()) return@withEnrichmentWakeLock

                // 3. Bound concurrent relay windows. When the cap is hit the
                //    cached render still stands; it just won't live-enrich.
                if (!windowLimiter.tryAcquire()) return@withEnrichmentWakeLock
                try {
                    observeUntilComplete(context, notificationId, account, users, notes, isComplete, build)
                } finally {
                    windowLimiter.release()
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeUntilComplete(
        context: Context,
        notificationId: String,
        account: Account,
        users: Collection<User>,
        notes: Collection<Note>,
        isComplete: () -> Boolean,
        build: suspend () -> Unit,
    ) {
        val userSubs = users.map { UserFinderQueryState(it, account) }
        val noteSubs = notes.map { EventFinderQueryState(it, account) }
        val authSub = ScreenAuthAccount(account)

        try {
            Amethyst.instance.authCoordinator.subscribe(authSub)
            userSubs.forEach {
                Amethyst.instance.sources.userFinder
                    .subscribe(it)
            }
            noteSubs.forEach {
                Amethyst.instance.sources.eventFinder
                    .subscribe(it)
            }

            coroutineScope {
                // Keep the relay pool connected for the duration of the window.
                val relayJob =
                    launch {
                        try {
                            withTimeout(WINDOW_MS) {
                                Amethyst.instance.relayProxyClientConnector.relayServices
                                    .collect()
                            }
                        } catch (_: CancellationException) {
                            // window elapsed or observation finished first
                        }
                    }

                // Re-render on metadata/content changes. Debounced so the
                // StateFlow initial-value burst and rapid arrivals collapse into
                // one rebuild. Stops as soon as everything needed is present, or
                // the user dismissed the notification in-app.
                val changes =
                    users.map { it.metadata().flow.map { } } +
                        notes.map {
                            it
                                .flow()
                                .metadata.stateFlow
                                .map { }
                        }

                if (changes.isNotEmpty()) {
                    withTimeoutOrNull(WINDOW_MS) {
                        merge(*changes.toTypedArray())
                            .debounce(RENDER_DEBOUNCE_MS)
                            .onEach { runBuild(build) }
                            .takeWhile { !NotificationUtils.wasDismissed(notificationId) && !isComplete() }
                            .collect { }
                    }
                }

                relayJob.cancel()
            }
        } finally {
            noteSubs.forEach {
                Amethyst.instance.sources.eventFinder
                    .unsubscribe(it)
            }
            userSubs.forEach {
                Amethyst.instance.sources.userFinder
                    .unsubscribe(it)
            }
            Amethyst.instance.authCoordinator.unsubscribe(authSub)
        }
    }

    private suspend fun runBuild(build: suspend () -> Unit) {
        try {
            build()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Notification build failed", e)
        }
    }

    private inline fun <T> withEnrichmentWakeLock(
        context: Context,
        block: () -> T,
    ): T {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock =
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "amethyst:notification_enrichment",
            )
        wakeLock.acquire(WINDOW_MS + 5_000L)
        try {
            return block()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
}
