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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.privacysandbox.ui.client.view.SandboxedSdkView

/**
 * The minimal surface controls [EmbeddedTabHost] needs to keep a session warm regardless of whether
 * it's a browser ([com.vitorpamplona.amethyst.ui.screen.loggedIn.browser.EmbeddedWebAppController])
 * or an nsite/napplet ([com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites.EmbeddedNostrAppController]).
 *
 * Warm-keep works by keeping the session's [SandboxedSdkView] **attached** to the window the whole
 * time (just moved off-screen when not active) — the privacy-sandbox view only closes its session on
 * detach, so an attached-but-hidden view stays alive. [onShown]/[onHidden] let a controller pause its
 * applet's JS while hidden; [teardown] tears the session down for good (eviction).
 */
@RequiresApi(Build.VERSION_CODES.R)
interface EmbeddedSurfaceController {
    fun attachView(view: SandboxedSdkView)

    /**
     * [view] left the composition. The controller outlives it (it lives in the process-scoped host), so it
     * must let go of it — a view holds its Activity, and a warm controller still pointing at the view of an
     * Activity the user backed out of would keep that whole Activity alive.
     */
    fun detachView(view: SandboxedSdkView)

    /** The session became the visible tab. */
    fun onShown() {
        // Optional hook: default no-op. Controllers that don't pause/resume applet JS need no action.
    }

    /** The session is warm but off-screen; a controller may pause its applet here. */
    fun onHidden() {
        // Optional hook: default no-op. Controllers that don't pause/resume applet JS need no action.
    }

    /**
     * The app left the screen ([visible] false) or came back. Even the visible tab has nobody looking at it
     * while the app is in the background, so a controller stops anything that acts for the user right away.
     */
    fun onAppVisibility(visible: Boolean) {
        // Optional hook: default no-op (napplet screens pause on their own lifecycle).
    }

    /**
     * The app has been in the background long enough that the rest of it winds down too (relays disconnect
     * at the same point): [idle] true pauses even the visible tab's page; false when the app returns.
     */
    fun onBackgroundIdle(idle: Boolean) {
        // Optional hook: default no-op (napplet screens pause on their own lifecycle).
    }

    /** Permanently close the session (unbind the service); used on eviction. */
    fun teardown()

    /**
     * Current main-frame load state, so [EmbeddedTabLayer] can draw a loading spinner / error+retry
     * overlay over this (z-below) surface — the surface itself sits above the nav screens, so the overlay
     * can't live in the screen. The default is "already loaded" (no overlay) for any controller that
     * doesn't report state.
     */
    val loadStatus: EmbeddedLoadStatus get() = EmbeddedLoadStatus(hasLoadedReal = true)

    /** Set by [EmbeddedTabLayer] for the active tab; notified on the main thread when [loadStatus] changes. */
    var onLoadStatusChanged: ((EmbeddedLoadStatus) -> Unit)?

    /** Re-attempt the load from scratch (the overlay's Retry); default no-op. */
    fun retry() {
        // Default no-op: only controllers that report a real load state (and thus can fail) override this.
    }
}
