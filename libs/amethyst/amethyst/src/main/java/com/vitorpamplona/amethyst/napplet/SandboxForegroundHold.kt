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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps Amethyst's **main** process in "resumed" resource posture while a full-screen sandbox surface
 * (a `:napplet`-process browser/napplet/nSite host) is in the foreground.
 *
 * The problem it solves: opening one of those full-screen surfaces backgrounds `MainActivity`. Its
 * Compose collectors (`ManageRelayServices` / `ManageWebOkHttp`) are lifecycle-aware, so they stop —
 * and the underlying `WhileSubscribed` flows then scale everything down on their timers: Tor's port
 * after ~2 s, the relay pool after ~30 s, dropping the relay AUTH sessions with it. The user is still
 * actively using a Nostr surface (the sandbox brokers NIP-07 + relays back through this very process),
 * so tearing the network down is wrong — the next signed request or relay read pays a full reconnect.
 *
 * The fix is a **ref-counted hold**, acquired only while such a surface is foreground. While the count
 * is > 0 it subscribes to exactly the flows the resumed UI subscribes to — keeping Tor, the relay pool,
 * and the AUTH sessions up. When the last surface leaves, it releases and normal background scaling
 * resumes. It deliberately does *not* keep anything alive when nothing is foreground, so the intended
 * battery/bandwidth scaling is preserved.
 *
 * Switching between several sandbox surfaces must not restart the network. Android tears the old
 * surface down and brings the new one up as `old.onPause() → new.onResume()`, which would dip the
 * count to 0 and back to 1. To keep the same collectors running straight through that dip, releasing to
 * 0 only *schedules* the stop after a short [LINGER_MS] linger; a re-acquire within the window cancels
 * the pending stop and the existing collectors are never even cancelled — so swapping between open
 * napplets/nSites/browser apps holds Tor + relays + AUTH perfectly steady.
 *
 * Embedded favorite tabs need no hold: they render inside `MainActivity`, which stays resumed.
 *
 * Threading: [acquire]/[release] are `@Synchronized` and may be called from any thread (Activity main
 * thread, broker IPC handler). The keep-alive collectors run on [AppModules.applicationIOScope].
 */
object SandboxForegroundHold {
    /**
     * How long the resource hold lingers after the last sandbox surface backgrounds, before the
     * collectors are actually torn down. Sized to comfortably bridge an activity-to-activity handoff
     * (`old.onPause → new.onResume`, plus the async IPC hop for `:napplet` surfaces) so switching
     * between open surfaces never restarts Tor/relays. A genuine exit still releases after this short
     * window, restoring normal background scaling.
     */
    private const val LINGER_MS = 5_000L

    private var holdCount = 0

    // The live keep-alive collectors. Non-null whenever the resources are being held up (including
    // during a linger after the count hit 0 but before the scheduled stop fires).
    private var holdJob: Job? = null

    // The pending teardown scheduled when the count hit 0; cancelled if a surface re-acquires first.
    private var stopJob: Job? = null

    @Synchronized
    fun acquire() {
        holdCount++
        if (holdCount == 1) {
            // Cancel any lingering teardown and keep the existing collectors running if they're still
            // up (the common "switch between two surfaces" path) — only start fresh if fully released.
            stopJob?.cancel()
            stopJob = null
            if (holdJob == null) start()
        }
    }

    @Synchronized
    fun release() {
        if (holdCount == 0) return
        holdCount--
        if (holdCount == 0) scheduleStop()
    }

    private fun start() {
        // Only ever invoked in the main process (broker + main-process activities); instance is set.
        val app = Amethyst.instance
        Log.d("SandboxForegroundHold", "Holding main-process resources up for a foreground sandbox surface")
        holdJob =
            app.applicationIOScope.launch {
                // Mirror exactly what the resumed UI collects (AndroidAppRoot's ManageRelayServices +
                // ManageWebOkHttp): keeping these subscribed keeps the relay pool connected (and so the
                // relay AUTH sessions), and — transitively, via the relay connector's combine and the
                // okHttp proxy-port provider — keeps Tor up too.
                launch { app.relayProxyClientConnector.relayServices.collect {} }
                launch { app.okHttpClients.defaultHttpClient.collect {} }
                launch { app.okHttpClients.defaultHttpClientWithoutProxy.collect {} }
            }
    }

    private fun scheduleStop() {
        stopJob?.cancel()
        stopJob =
            Amethyst.instance.applicationIOScope.launch {
                delay(LINGER_MS)
                synchronized(this@SandboxForegroundHold) {
                    // A surface may have re-acquired while we waited; only tear down if still released.
                    if (holdCount == 0) {
                        Log.d("SandboxForegroundHold") { "No foreground sandbox surface for ${LINGER_MS}ms; releasing the resource hold" }
                        holdJob?.cancel()
                        holdJob = null
                    }
                    stopJob = null
                }
            }
    }
}
