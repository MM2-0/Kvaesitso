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
package com.vitorpamplona.amethyst.service.nests

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.vitorpamplona.amethyst.commons.viewmodels.NestNetworkChangeBus
import com.vitorpamplona.quartz.utils.Log

/**
 * Pure-state foreground/background tracker, decoupled from
 * `android.app.Activity` so the unit tests can drive it without
 * Robolectric or Mockito.
 *
 * See [AppForegroundRecycleHook] for the production motivation /
 * threshold-rationale kdoc — this class is just the testable core.
 *
 * Threading: all state-mutating methods are documented to run on the
 * Android main thread (Application lifecycle callbacks fire there);
 * tests call them serially, so no synchronisation is needed.
 */
class AppForegroundCounter(
    private val backgroundThresholdMs: Long = AppForegroundRecycleHook.DEFAULT_BACKGROUND_THRESHOLD_MS,
    private val publishEvent: () -> Unit = { NestNetworkChangeBus.publish() },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private var startedActivities = 0
    private var lastBackgroundedAtMillis: Long = -1L

    /**
     * Count of recycle events fired since construction. Diagnostic
     * surface for tests; production code observes the side-effect via
     * [NestNetworkChangeBus] instead.
     */
    var recyclesFired: Int = 0
        private set

    /**
     * Increment the started-activity counter and, if this is the
     * 0 → 1 transition AND the app spent ≥ [backgroundThresholdMs]
     * in the background, fire [publishEvent]. The first
     * onActivityStarted after process start is a no-op (no prior
     * background timestamp to compare against).
     */
    fun onActivityStarted() {
        val wasBackgrounded = startedActivities == 0
        startedActivities++
        if (!wasBackgrounded) return
        val backgroundedAt = lastBackgroundedAtMillis
        if (backgroundedAt < 0L) return
        val backgroundedFor = nowMillis() - backgroundedAt
        if (backgroundedFor < backgroundThresholdMs) {
            Log.d("AppForegroundCounter") {
                "skipping recycle on resume after only ${backgroundedFor}ms background " +
                    "(threshold=${backgroundThresholdMs}ms)"
            }
            return
        }
        Log.d("AppForegroundCounter") {
            "publishing recycle event on resume after ${backgroundedFor}ms background"
        }
        recyclesFired++
        publishEvent()
    }

    /**
     * Decrement the counter; on N → 0 transition, record the
     * background timestamp.
     */
    fun onActivityStopped() {
        startedActivities--
        if (startedActivities <= 0) {
            startedActivities = 0
            lastBackgroundedAtMillis = nowMillis()
        }
    }
}

/**
 * Application-wide observer that publishes a
 * [NestNetworkChangeBus] event when the app returns to the foreground
 * after spending more than [backgroundThresholdMs] in the background.
 *
 * Production motivation: Android may reclaim a backgrounded app's
 * UDP-socket file descriptors as it ages out of the foreground app
 * pool (the kernel's watcher trims after roughly 30 s of no foreground
 * activity, with quite a bit of variance per OEM). When the user
 * resumes the app, the QUIC connection sitting on the now-reclaimed
 * socket has dead OS-level state, but the connection-level FSM
 * doesn't know that yet — the next `socket.send` throws, and only
 * then does the send-loop catch surface CLOSED.
 *
 * The downstream
 * [com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel]
 * already observes [NestNetworkChangeBus] for the network-handover
 * case (Wi-Fi ↔ cellular). We piggy-back on the same bus here: a
 * "long enough background" event has the same shape from the QUIC
 * driver's perspective as a network change — recycle the underlying
 * session and let the [com.vitorpamplona.nestsclient.connectReconnectingNestsListener]
 * / `connectReconnectingNestsSpeaker` orchestrators reconnect.
 *
 * Why a threshold instead of fire-on-every-resume:
 *  - A short background (notification pulldown, biometric auth, lock-
 *    screen glance) lasts < 1 s and the socket is still healthy. A
 *    forced recycle there is a wasted ~1 s re-handshake gap of audio
 *    silence — annoying to users for no benefit.
 *  - A long background (call from another app, screen off > 30 s,
 *    home-button-then-back) commonly leaves the socket dead. Better
 *    to eat one re-handshake gap than 30 s of silence while the QUIC
 *    PTO times out.
 * 5 seconds is the sweet spot: well over typical UI transitions but
 * well under any plausible socket-reclaim window.
 */
class AppForegroundRecycleHook(
    backgroundThresholdMs: Long = DEFAULT_BACKGROUND_THRESHOLD_MS,
    publishEvent: () -> Unit = { NestNetworkChangeBus.publish() },
    nowMillis: () -> Long = { System.currentTimeMillis() },
) : Application.ActivityLifecycleCallbacks {
    private val counter = AppForegroundCounter(backgroundThresholdMs, publishEvent, nowMillis)

    override fun onActivityStarted(activity: Activity) {
        counter.onActivityStarted()
    }

    override fun onActivityStopped(activity: Activity) {
        counter.onActivityStopped()
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        /**
         * Default 5 000 ms — well above the longest plausible UI
         * transition (notification pull, biometric prompt) and well
         * below the 30 s timing the Android kernel uses to reclaim
         * idle UDP sockets.
         */
        const val DEFAULT_BACKGROUND_THRESHOLD_MS = 5_000L
    }
}
