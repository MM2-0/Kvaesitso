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
package com.vitorpamplona.amethyst.commons.util

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.concurrent.Volatile

/**
 * Collapses a burst of edits into a single [publish], run once the edits stop.
 *
 * Superseding a still-pending publish cancels it. That is safe for a whole-state publish — one that
 * reads the current state when it runs rather than carrying a payload — because the replacement
 * carries the same state or newer; it is wrong for anything that publishes a delta. A publish that
 * already started is left to finish (it may be parked on a remote signer), and the new edit gets a
 * publish of its own; [publish] must order the two, as `saveNewAppSpecificData` does.
 *
 * A pending edit lives entirely in the [Job] that [launch] returned, so it is only as durable as the
 * scope that job belongs to — see `aPendingPublishDiesWithTheScopeItWasLaunchedOn`. Give [launch] a
 * scope that outlives every teardown the edit has to survive.
 *
 * [launch] is injected rather than a [kotlinx.coroutines.CoroutineScope] so the caller chooses that
 * scope and keeps its own error handling (for [AccountViewModel], the signer-exception toasts), and
 * so the semantics here can be unit-tested on a test scope.
 */
class DebouncedPublisher(
    private val debounceMs: Long,
    private val launch: (suspend () -> Unit) -> Job,
    private val publish: suspend () -> Unit,
) {
    /**
     * One scheduled publish. [started] flips once its wait is over: from then on it is a publish in
     * flight, which nothing here cancels or hurries, since it may be parked on a remote signer and a
     * restart would send a second request. The flag lives on each publish, not on this class, so the
     * late write of an older one, on its own thread, cannot mark a newer one as started.
     */
    private class Pending {
        @Volatile var job: Job? = null

        @Volatile var started = false
    }

    @Volatile
    private var pending: Pending? = null

    /** Records an edit: restarts the wait, so a run of edits publishes once, after the last one. */
    fun schedule() = start(debounceMs)

    /**
     * Publishes a pending edit now instead of waiting out the debounce. A no-op when nothing is
     * pending, so a caller can flush on every exit path without publishing the same state twice.
     */
    fun flush() {
        val current = pending ?: return
        if (current.started || current.job?.isActive != true) return
        start(0)
    }

    private fun start(delayMs: Long) {
        // Only a publish still waiting is superseded. If one is just starting on its own thread the
        // cancel can still land, but before it sent anything, and the publish launched below goes out.
        pending?.let { if (!it.started) it.job?.cancel() }
        val next = Pending()
        next.job =
            launch {
                delay(delayMs)
                next.started = true
                publish()
            }
        pending = next
    }
}
