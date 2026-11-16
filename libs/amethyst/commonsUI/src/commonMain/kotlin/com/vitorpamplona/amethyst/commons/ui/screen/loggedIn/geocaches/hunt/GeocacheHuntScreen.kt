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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.hunt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.GeocacheTab
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_progress
import com.vitorpamplona.amethyst.commons.resources.geocache_hunt_start
import com.vitorpamplona.amethyst.commons.resources.geocache_loading
import com.vitorpamplona.amethyst.commons.resources.geocache_unnamed
import com.vitorpamplona.amethyst.commons.resources.route_geocache_hunt_detail
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GeocacheSpecLine
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.geocacheEmoji
import com.vitorpamplona.amethyst.commons.ui.note.rememberGeocachePalette
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.datasource.GeocachesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.rememberMyFoundCacheIds
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent

/**
 * One curated hunt: a banner, a progress bar and the caches in the order the curator put them.
 *
 * That order is load-bearing — NIP-CC says the `a` tags are meaningful in sequence, and
 * `curatedGeocaches()` preserves it — so the rows are numbered rather than sorted by distance or
 * date. A hunt is a route someone designed, not a list of nearby things.
 *
 * `theme` and `map` on the list are read as the curator's *defaults*, which is what the spec
 * asks for. Amethyst renders in the reader's own theme regardless; honouring a stranger's
 * colour scheme over the user's chosen one is not a trade this app makes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeocacheHuntScreen(
    kind: Int,
    pubKeyHex: String,
    dTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    GeocachesFilterAssemblerSubscription(accountViewModel)

    val address = remember(kind, pubKeyHex, dTag) { Address(kind, pubKeyHex, dTag) }
    val huntNote = remember(address) { LocalCache.getOrCreateAddressableNote(address) }
    val noteState by observeNote(huntNote, accountViewModel)
    val hunt = noteState.note.event as? GeocacheCurationListEvent

    val caches = remember(hunt) { hunt?.geocaches().orEmpty() }
    val found = rememberMyFoundCacheIds(accountViewModel)
    val doneCount = remember(caches, found) { caches.count { found.contains(it.toValue()) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.route_geocache_hunt_detail)) },
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = stringRes(Res.string.back),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                .consumeWindowInsets(pad)
                .imePaddingSafe()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (hunt == null) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringRes(Res.string.geocache_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            hunt.image()?.trim()?.ifBlank { null }?.takeIf { accountViewModel.settings.showImages() }?.let {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    mainImageModifier = Modifier,
                    loadedImageModifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)),
                    accountViewModel = accountViewModel,
                    onLoadingBackground = null,
                    onError = null,
                )
            }

            Text(
                text = hunt.title()?.trim().orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            hunt.description()?.trim()?.ifBlank { null }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinearProgressIndicator(
                progress = { if (caches.isEmpty()) 0f else doneCount.toFloat() / caches.size },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = rememberGeocachePalette().proven,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )

            Text(
                text = stringRes(Res.string.geocache_hunt_progress, doneCount, caches.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { nav.nav(Route.Geocaches(GeocacheTab.MAP)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringRes(Res.string.geocache_hunt_start)) }

            caches.forEachIndexed { index, cacheAddress ->
                GeocacheHuntStop(
                    position = index + 1,
                    address = cacheAddress,
                    isFound = found.contains(cacheAddress.toValue()),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun GeocacheHuntStop(
    position: Int,
    address: Address,
    isFound: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { cacheNote ->
        // A hunt is a list of caches the reader has most likely never fetched, so the note comes
        // back empty and `cacheNote.event` would stay null forever. observeNoteEvent asks the
        // relays for it and recomposes this stop when it lands.
        val listing = cacheNote?.let { observeNoteEvent<GeocacheListingEvent>(it, accountViewModel).value }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.GeocacheDetail(address)) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isFound) "$position ✓" else "$position",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isFound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(Modifier.weight(1f)) {
                Text(
                    text =
                        listing?.cacheName()?.trim()?.ifBlank { null }
                            ?: stringRes(Res.string.geocache_unnamed),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (listing != null) {
                    GeocacheSpecLine(listing.cacheType(), listing.cacheSize(), listing.difficulty(), listing.terrain())
                }
            }

            Text(
                text = listing?.cacheType().geocacheEmoji(),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
