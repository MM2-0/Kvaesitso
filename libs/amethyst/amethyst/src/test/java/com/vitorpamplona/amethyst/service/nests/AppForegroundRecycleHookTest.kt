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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [AppForegroundCounter] — the pure-state core of
 * [AppForegroundRecycleHook]. Drives the lifecycle transitions
 * directly with a controllable clock so the threshold logic doesn't
 * need a real wall-clock wait.
 */
class AppForegroundRecycleHookTest {
    @Test
    fun firstForegroundAfterProcessStartDoesNotPublish() {
        // The very first onActivityStarted has no prior background to
        // recycle from — recycling here would fire a redundant
        // re-handshake on every cold start, which is wasteful.
        var fakeNow = 0L
        var publishCount = 0
        val counter =
            AppForegroundCounter(
                publishEvent = { publishCount++ },
                nowMillis = { fakeNow },
            )

        fakeNow = 1_000L
        counter.onActivityStarted()

        assertEquals("first onActivityStarted must not publish — no prior background", 0, publishCount)
        assertEquals(0, counter.recyclesFired)
    }

    @Test
    fun shortBackgroundDoesNotTriggerRecycle() {
        // 1 s background (notification pull, biometric prompt) is
        // typical UI noise and the QUIC socket is still healthy.
        var fakeNow = 0L
        var publishCount = 0
        val counter =
            AppForegroundCounter(
                backgroundThresholdMs = 5_000L,
                publishEvent = { publishCount++ },
                nowMillis = { fakeNow },
            )

        fakeNow = 1_000L
        counter.onActivityStarted()
        fakeNow = 2_000L
        counter.onActivityStopped()
        fakeNow = 3_000L // backgrounded for 1 s only
        counter.onActivityStarted()

        assertEquals(
            "background < threshold must not publish — short transitions don't reclaim sockets",
            0,
            publishCount,
        )
    }

    @Test
    fun backgroundLongerThanThresholdTriggersRecycle() {
        // 6 s background crosses the 5 s default threshold — Android
        // may have reclaimed the socket FD by now, so recycle the
        // QUIC session on resume.
        var fakeNow = 0L
        var publishCount = 0
        val counter =
            AppForegroundCounter(
                backgroundThresholdMs = 5_000L,
                publishEvent = { publishCount++ },
                nowMillis = { fakeNow },
            )

        fakeNow = 1_000L
        counter.onActivityStarted()
        fakeNow = 2_000L
        counter.onActivityStopped()
        fakeNow = 8_000L // backgrounded for 6 s
        counter.onActivityStarted()

        assertEquals(
            "background ≥ threshold must publish exactly once on resume",
            1,
            publishCount,
        )
        assertEquals(1, counter.recyclesFired)
    }

    @Test
    fun multipleActivitiesTrackTransitionCorrectly() {
        // Picture-in-picture mode is implemented as a second activity
        // overlaid on the main activity. While both are started the
        // app is in foreground; only when both stop does the app
        // truly background.
        var fakeNow = 0L
        var publishCount = 0
        val counter =
            AppForegroundCounter(
                backgroundThresholdMs = 5_000L,
                publishEvent = { publishCount++ },
                nowMillis = { fakeNow },
            )

        // First activity starts (cold start), no publish.
        fakeNow = 1_000L
        counter.onActivityStarted()
        // Second activity (e.g. PIP / dialog) starts on top. Still
        // foreground; no publish — `wasBackgrounded` was false.
        fakeNow = 2_000L
        counter.onActivityStarted()
        // First activity stops (e.g. user backs out of main).
        // counter = 1, still foreground.
        fakeNow = 3_000L
        counter.onActivityStopped()
        assertEquals(
            "intermediate stop with another activity still started must not background",
            0,
            publishCount,
        )
        // Second stops → app truly backgrounds.
        fakeNow = 4_000L
        counter.onActivityStopped()
        // 6 s later, an activity restarts → recycle.
        fakeNow = 10_000L
        counter.onActivityStarted()
        assertEquals(1, publishCount)
    }

    @Test
    fun consecutiveLongBackgroundsEachPublishOnce() {
        // Two separate back-and-forth cycles must each fire exactly
        // one publish. A regression that misses to refresh the
        // last-backgrounded timestamp on second-stop would either
        // double-fire on the second resume or skip it.
        var fakeNow = 0L
        var publishCount = 0
        val counter =
            AppForegroundCounter(
                backgroundThresholdMs = 5_000L,
                publishEvent = { publishCount++ },
                nowMillis = { fakeNow },
            )

        // Cycle 1: cold start → 6 s background → resume (publish #1)
        fakeNow = 1_000L
        counter.onActivityStarted()
        fakeNow = 2_000L
        counter.onActivityStopped()
        fakeNow = 8_000L
        counter.onActivityStarted()
        assertEquals("first resume after long background must publish", 1, publishCount)

        // Cycle 2: 8 s background again → resume (publish #2)
        fakeNow = 10_000L
        counter.onActivityStopped()
        fakeNow = 18_000L
        counter.onActivityStarted()
        assertEquals("second resume after long background must also publish", 2, publishCount)
    }
}
