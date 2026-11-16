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

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.vitorpamplona.amethyst.napplet.NappletWebViewProfiles

/**
 * Makes every embedded tab (browser / nsite / napplet) follow an account switch.
 *
 * Each account gets its own WebView storage jar, and that partition is chosen once, when the WebView is
 * constructed — so a warm session built for account A can never serve account B. This asks
 * [EmbeddedTabHost] to tear down and re-warm every session whenever the active account's profile changes;
 * see [EmbeddedTabHost.rebuildIfProfileChanged] for why reusing them also leaves the tab blank.
 *
 * The whole logged-in subtree is recreated per account, so this composable is itself brand new after a
 * switch — which is exactly why the "which account are the warm sessions built for" marker lives in
 * [EmbeddedTabHost] (process-scoped) and not in a `remember` here.
 *
 * Mount once next to [EmbeddedTabLayer]/[EmbeddedTabPreloader], before them so the stale sessions are
 * dropped ahead of the first preload sweep. Draws nothing.
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun EmbeddedTabAccountWatcher() {
    // Read the same opaque profile name the controllers stamp on their sessions, from the same source, so
    // the sessions we rebuild are guaranteed to be built against the account we just checked for.
    val profile = NappletWebViewProfiles.current()

    // SideEffect, not LaunchedEffect: this must land in the SAME frame as the switch. A LaunchedEffect
    // dispatches through the composition's coroutine scope, and an account switch floods the main thread —
    // measured 3.0 s and 4.3 s of delay on a real device. For that whole window the tab layer had already
    // rebuilt every SandboxedSdkView against the surviving (now-dead) controllers, so every embedded tab
    // was a black rectangle. SideEffect runs at the end of the apply phase, so the stale sessions are torn
    // down and the epoch bumped before the next composition renders any surface.
    //
    // Safe to run on every recomposition: [EmbeddedTabHost.rebuildIfProfileChanged] is idempotent — it
    // compares against the profile the warm sessions were built for and no-ops when nothing changed.
    SideEffect { EmbeddedTabHost.rebuildIfProfileChanged(profile) }
}
