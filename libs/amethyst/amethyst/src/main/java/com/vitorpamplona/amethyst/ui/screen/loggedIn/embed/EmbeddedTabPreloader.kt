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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.navigation.favoriteIds
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Warms every bottom-bar favorite at startup so the first tap is instant — the surfaces are built,
 * attached (off-screen), and their content downloaded before the user ever opens them. Idle work that
 * shares the exact warm sessions the favorite screens use (same ids via [EmbeddedTabFactory]).
 *
 * Mount once next to [EmbeddedTabLayer]. Draws nothing; it just drives acquisition. Napplet favorites
 * whose events haven't synced yet, and Tor-routed sites whose proxy isn't up yet, are retried for a
 * bounded window (the latter so a clearnet preload can never race ahead of Tor) — that retry loop, and
 * everything else that has to suspend, lives in [EmbeddedTabPreloadSweeper]; this composable only kicks
 * it off, synchronously.
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun EmbeddedTabPreloader(accountViewModel: AccountViewModel) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

    val bottomBarItems by accountViewModel.account.settings.syncedSettings.navigation.bottomBarItems
        .collectAsStateWithLifecycle()
    val favoriteIds = bottomBarItems.favoriteIds()

    // Subscribe to the epoch here (a read inside a SideEffect wouldn't recompose us), so a bump that lands
    // outside our apply phase — [EmbeddedTabThemeWatcher] bumps it from a LaunchedEffect — still re-runs the
    // kickoff below. The SideEffect re-reads it, so a bump in the SAME apply phase is picked up immediately.
    val observedEpoch = EmbeddedTabHost.rebuildEpoch

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Give preloaded surfaces a realistic viewport before any tab is visited, so they download as a
    // full-size page rather than at the 1dp off-screen fallback. The first real visit corrects it.
    // Synchronous, and declared BEFORE the kickoff, because the kickoff is synchronous too: as a
    // LaunchedEffect this would now land *after* the first preload and hand it the off-screen fallback.
    SideEffect {
        with(density) {
            EmbeddedTabHost.seedBoundsIfUnset(Rect(0f, 0f, configuration.screenWidthDp.dp.toPx(), configuration.screenHeightDp.dp.toPx()))
        }
    }

    // Re-warms after a theme flip or an account switch: [rebuildAll] tears down the warm sessions and bumps
    // the epoch, so this sweep re-acquires them freshly built (keying on the epoch also orders it after the
    // teardown). This is also what re-warms tabs the user never opened, so none survives bound to the old
    // account's storage profile.
    //
    // SideEffect, not LaunchedEffect: [EmbeddedTabAccountWatcher] tears the sessions down in the apply phase
    // of the switch, and until this sweep runs there is nothing for the tab to show. A LaunchedEffect
    // dispatches through the composition's scope, and an account switch floods the main thread — the very
    // congestion that made the watcher itself fire 3-4 s late. Running here re-arms the tabs in the same
    // frame that dropped them. The epoch is re-read inside the lambda so we see the watcher's bump (its
    // SideEffect is ordered before ours), and [EmbeddedTabPreloadSweeper.request] is idempotent, so running
    // on every recomposition is free.
    SideEffect {
        EmbeddedTabPreloadSweeper.request(context, favoriteIds, backgroundColor, maxOf(observedEpoch, EmbeddedTabHost.rebuildEpoch))
    }
}
