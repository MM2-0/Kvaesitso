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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.navigation.favoriteIds
import com.vitorpamplona.amethyst.napplet.NsiteNetworkRegistry
import com.vitorpamplona.amethyst.napplet.WebAppNetworkRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

// Up to PRELOAD_ATTEMPTS sweeps, PRELOAD_RETRY_MS apart, give a slow napplet event or a still-connecting
// Tor proxy time to settle before we give up and leave that tab to load on first visit (~45 s total).
private const val PRELOAD_ATTEMPTS = 15
private const val PRELOAD_RETRY_MS = 3_000L

/**
 * Process-scoped owner of the bottom-bar warm-up sweep, driven by [EmbeddedTabPreloader].
 *
 * **Why this isn't just a `LaunchedEffect` in the composable.** After an account switch every warm
 * session is torn down ([EmbeddedTabHost.rebuildAll]) and must be rebuilt against the new storage
 * profile — until that happens the tabs have nothing to show. A `LaunchedEffect` dispatches its body
 * through the composition's coroutine scope, and an account switch floods the main thread: the same
 * construct made [EmbeddedTabAccountWatcher] fire 3-4 s late before it became a `SideEffect`. Waiting
 * that long to even *start* the re-warm is what left the tab blank for seconds after a switch.
 *
 * So the **kickoff** is synchronous — [request] is called from a `SideEffect`, in the same apply phase
 * that bumped the epoch — while the sweep itself stays a coroutine, because it genuinely has to suspend
 * (it awaits the network-registry hydration, then retries pending favorites for ~45 s).
 * [CoroutineStart.UNDISPATCHED] on [Dispatchers.Main.immediate] means the body runs *inline* up to its
 * first real suspension, so on a re-warm (registries already hydrated, so `awaitReady` returns without
 * suspending) the first tab is rebuilt before [request] even returns.
 */
@RequiresApi(Build.VERSION_CODES.R)
object EmbeddedTabPreloadSweeper {
    private data class SweepKey(
        val favoriteIds: List<String>,
        val backgroundColor: Int,
        val rebuildEpoch: Int,
    )

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var job: Job? = null
    private var lastKey: SweepKey? = null

    /**
     * Starts (or restarts) the sweep for [favoriteIds]. Idempotent: repeated calls with the same inputs
     * no-op, so it is safe to call from a `SideEffect` that runs on every recomposition. A changed
     * [rebuildEpoch] — a theme flip or an account switch — cancels the in-flight sweep and starts a fresh
     * one, which is what re-warms the tabs the user never opened so none survives bound to the old
     * account's storage profile.
     */
    fun request(
        context: Context,
        favoriteIds: List<String>,
        backgroundColor: Int,
        rebuildEpoch: Int,
    ) {
        val key = SweepKey(favoriteIds, backgroundColor, rebuildEpoch)
        if (lastKey == key) return
        lastKey = key
        job?.cancel()
        job = null
        if (favoriteIds.isEmpty()) return
        // The sweep outlives any single composition, so it must not pin an Activity.
        val appContext = context.applicationContext
        job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                sweep(appContext, favoriteIds, backgroundColor)
            }
    }

    private suspend fun CoroutineScope.sweep(
        context: Context,
        favoriteIds: List<String>,
        backgroundColor: Int,
    ) {
        // Hydrate the per-site Tor/open-web choices BEFORE the first preload: a cold start otherwise reads
        // the bare Tor default and would route a site the user pinned to the open web through Tor (or stall
        // it waiting for Tor), which is exactly what breaks Tor-incompatible servers. On a re-warm these
        // are already hydrated, so they return without suspending and the first tab is built inline.
        WebAppNetworkRegistry.init(context)
        NsiteNetworkRegistry.preload()
        WebAppNetworkRegistry.awaitReady()
        NsiteNetworkRegistry.awaitReady()
        var attempt = 0
        while (isActive) {
            val byId =
                Amethyst.instance.favoriteApps.favorites.value
                    .associateBy { it.id }
            var stillPending = false
            for (id in favoriteIds) {
                // Not loaded from disk yet (the favorites store hydrates asynchronously): retry, rather than
                // letting the sweep end having warmed nothing.
                val app = byId[id]
                if (app == null) {
                    stillPending = true
                    continue
                }
                if (!EmbeddedTabFactory.preload(context, app, backgroundColor)) stillPending = true
                // Each preload may build + attach a WebView on this (main) thread; yield between favorites
                // so the sweep doesn't monopolize the frame and jank the paint that follows.
                yield()
            }
            if (!stillPending || ++attempt >= PRELOAD_ATTEMPTS) break
            delay(PRELOAD_RETRY_MS)
        }
    }
}
