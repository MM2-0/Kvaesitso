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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.favorites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// If relays stay silent this long after launch, fall back to the device-cached manifest so a pinned
// nsite/napplet can still resolve offline. Kept short because the embedded preloader retries on its
// own (~45 s) budget — we only need the manifest in the cache before one of those sweeps lands.
private const val MANIFEST_OFFLINE_FALLBACK_MS = 2_000L

/**
 * Keeps every favorited nsite/napplet (a [FavoriteApp.NostrApp]) resolvable in [LocalCache] the way a
 * pinned web app's URL always is — closing the gap where Android's `EmbeddedTabPreloader` could not warm a
 * Nostr-app favorite because its manifest event had simply never been fetched (favorites store only a
 * `kind:pubkey:dtag` coordinate, and nothing pulled that addressable event in until the user opened
 * the napplet/nsite discovery screen). For each favorite this:
 *
 *  1. subscribes to the manifest's addressable coordinate (via [observeNote] → the EventFinder), so the
 *     latest version is pulled from the author's relays into [LocalCache];
 *  2. persists whatever version resolves back into the device-local favorites store, so the next cold
 *     start (or an offline one) has it immediately; and
 *  3. if relays stay silent shortly after launch, seeds [LocalCache] from that cached copy.
 *
 * Mounted once in the logged-in shell, independent of the API-30 embedded-surface gate: the
 * full-screen launcher ([com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher]) benefits from a
 * resolved manifest on every device too. Draws nothing; it just drives acquisition.
 */
@Composable
fun FavoriteAppManifestPreloader(accountViewModel: AccountViewModel) {
    val registry = LocalAppServices.current.favoriteApps
    val favorites by registry.favorites.collectAsStateWithLifecycle()
    val coordinates =
        remember(favorites) {
            favorites.filterIsInstance<FavoriteApp.NostrApp>().map { it.coordinate }
        }

    coordinates.forEach { coordinate ->
        key(coordinate) {
            WatchFavoriteManifest(coordinate, registry, accountViewModel)
        }
    }
}

@Composable
private fun WatchFavoriteManifest(
    coordinate: String,
    registry: FavoriteAppsRegistry,
    accountViewModel: AccountViewModel,
) {
    val note = remember(coordinate) { LocalCache.checkGetOrCreateAddressableNote(coordinate) } ?: return

    // Drives the relay fetch while the manifest is missing AND observes LocalCache for the resolved
    // (or any newer) version arriving from relays.
    val noteState by observeNote(note, accountViewModel)
    val event = noteState.note.event

    // Persist the freshest manifest we have so the next launch resolves instantly. Keyed on the event
    // id so a republished manifest (new version → new id) replaces the cached copy; the very first
    // value also covers the "just favorited it" case without touching the add sites.
    LaunchedEffect(event?.id) {
        val resolved = event ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            registry.cacheManifest(coordinate, resolved.toJson())
        }
    }

    // Offline / slow-relay fallback: if nothing has arrived shortly after mount, seed LocalCache from
    // the cached copy. Guarded twice so we never overwrite a version the relays did deliver, and
    // consumed with wasVerified=false so the cached event's signature is re-checked before we trust it.
    LaunchedEffect(coordinate) {
        if (LocalCache.getAddressableNoteIfExists(coordinate)?.event != null) return@LaunchedEffect
        delay(MANIFEST_OFFLINE_FALLBACK_MS)
        if (LocalCache.getAddressableNoteIfExists(coordinate)?.event != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val cached = registry.cachedManifest(coordinate) ?: return@withContext
            Event.fromJsonOrNull(cached)?.let { LocalCache.justConsume(it, null, false) }
        }
    }
}
