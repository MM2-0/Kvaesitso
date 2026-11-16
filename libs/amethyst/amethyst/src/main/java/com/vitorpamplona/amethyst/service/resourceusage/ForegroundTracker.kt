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
package com.vitorpamplona.amethyst.service.resourceusage

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level foreground signal as an observable StateFlow: true while at
 * least one activity is STARTED. Used by the usage ledger to attribute bytes
 * and connection-time to foreground vs background buckets.
 *
 * ([MainActivity.isResumed] is not observable and goes false during PiP /
 * in-app dialogs, which would misattribute foreground traffic to background.)
 */
class ForegroundTracker : Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0

    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        _isForeground.value = startedActivities > 0
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        _isForeground.value = startedActivities > 0
    }

    // Only started/stopped drive the foreground signal; the remaining lifecycle
    // callbacks are required by the interface but irrelevant to this tracker.
    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        // No-op: creation does not change foreground state.
    }

    override fun onActivityResumed(activity: Activity) {
        // No-op: foreground is derived from started/stopped counts, not resume.
    }

    override fun onActivityPaused(activity: Activity) {
        // No-op: foreground is derived from started/stopped counts, not pause.
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {
        // No-op: state saving does not change foreground state.
    }

    override fun onActivityDestroyed(activity: Activity) {
        // No-op: onActivityStopped already decremented the counter.
    }
}
