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
package com.vitorpamplona.amethyst.commons.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Pre-fetches the addressable events behind the user's favorited [FavoriteApp.NostrApp]s (nSites /
 * nApplets) while a screen that can launch them is on screen.
 *
 * A favorite stores only the addressable coordinate `kind:pubkey:dtag`, never the event. The launch
 * path ([FavoriteAppLauncher.launchNostrApp] / [FavoriteAppLauncher.embedParams]) re-resolves the live
 * event from [LocalCache] at tap time, so a favorite whose event hasn't streamed in yet can't launch —
 * the user gets the "isn't loaded yet" toast / unavailable tab. Before this preloader, the only thing
 * that pulled those events into the cache was visiting the nsite/napplet feed (it subscribes by author),
 * which is why opening that feed and coming back made a favorite suddenly launchable.
 *
 * This subscribes each favorited coordinate to the shared
 * [EventFinder][com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderFilterAssembler]
 * — the same lifecycle-aware loader [observeNote][com.vitorpamplona.amethyst.commons.relayClient.event.observeNote]
 * uses — so the manifests fetch (via the author's outbox relays) as soon as the launcher opens and are
 * already in [LocalCache] by the time the user taps. The loader drops each coordinate from its filter
 * once the event arrives, so this is a one-shot fetch, not a standing feed.
 */
@Composable
fun PreloadFavoriteNostrApps(
    apps: List<FavoriteApp>,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    // Reuse the same query-state instances across recompositions (the manager ref-counts by identity),
    // recomputing only when the favorite list or account changes. Events that already loaded are still
    // included; the loader simply skips them because their note already has an event.
    val states =
        remember(apps, account) {
            apps
                .filterIsInstance<FavoriteApp.NostrApp>()
                .mapNotNull { LocalCache.checkGetOrCreateAddressableNote(it.coordinate) }
                .map { EventFinderQueryState(it, account) }
        }

    LifecycleAwareKeyDataSourceSubscription(states, accountViewModel.dataSources().eventFinder)
}
