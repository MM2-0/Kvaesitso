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

/**
 * Decides when an embedded tab rebuilds itself after its sandbox-side surface died underneath it.
 *
 * Every WebView in `:napplet` shares one renderer process. When the OS reclaims it (memory pressure after
 * a long session, the app sitting in the background) or it crashes, EVERY warm tab loses its WebView at
 * once, and a surface whose remote session errored out paints nothing — both leave a black rectangle that
 * never comes back on its own. The tab was already loaded, so no load overlay covers it either.
 *
 * Recovery is automatic but lazy: the visible tab rebuilds right away, a parked one only when it is next
 * shown, so a renderer death doesn't rebuild every warm tab at once and push memory straight back up. A
 * page that kills its renderer again right after an automatic rebuild is not rebuilt in a loop — the tab
 * falls back to the error overlay and its Retry.
 *
 * Main-thread only. [now] is injectable for tests.
 */
class EmbeddedAutoRecovery(
    private val now: () -> Long,
) {
    enum class Decision {
        /** Rebuild now: the tab is on screen. */
        RECOVER_NOW,

        /** The tab is parked; [onShown] will ask for the rebuild. */
        DEFERRED,

        /** It died again right after an automatic rebuild: stop and let the user retry. */
        GIVE_UP,
    }

    private var shown = false

    /** Whether the tab is the visible one (per the last [onShown] / [onHidden]). */
    val isShown: Boolean get() = shown
    private var pending = false
    private var lastAutoRecoveryAt: Long? = null

    /** The tab's surface died. */
    fun onLost(): Decision {
        if (!shown) {
            pending = true
            return Decision.DEFERRED
        }
        val last = lastAutoRecoveryAt
        if (last != null && now() - last < LOOP_WINDOW_MS) {
            pending = false
            return Decision.GIVE_UP
        }
        lastAutoRecoveryAt = now()
        return Decision.RECOVER_NOW
    }

    /** The tab became visible. Returns true when a deferred rebuild must run now. */
    fun onShown(): Boolean {
        shown = true
        if (!pending) return false
        pending = false
        lastAutoRecoveryAt = now()
        return true
    }

    fun onHidden() {
        shown = false
    }

    /** The surface came back by other means (a fresh session, a user retry): nothing left to rebuild. */
    fun clearPending() {
        pending = false
    }

    companion object {
        /** A second death this soon after an automatic rebuild means the page itself is killing it. */
        const val LOOP_WINDOW_MS = 30_000L
    }
}
