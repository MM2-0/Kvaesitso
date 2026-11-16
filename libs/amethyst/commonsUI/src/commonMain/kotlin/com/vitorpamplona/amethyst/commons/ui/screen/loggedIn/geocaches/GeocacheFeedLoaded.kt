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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_empty_finds
import com.vitorpamplona.amethyst.commons.resources.geocache_empty_hunts
import com.vitorpamplona.amethyst.commons.resources.geocache_empty_mine
import com.vitorpamplona.amethyst.commons.resources.geocache_empty_nearby
import com.vitorpamplona.amethyst.commons.resources.geocache_sorted_by_distance
import com.vitorpamplona.amethyst.commons.resources.geocache_sorted_by_recent
import com.vitorpamplona.amethyst.commons.resources.geocache_use_my_location
import com.vitorpamplona.amethyst.commons.service.georelay.GeoRelayDirectory
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.geocachePoint
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGeocache
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGeocacheFoundLog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent

/** Which of the hub's list tabs is rendering, which decides the row and the empty copy. */
enum class GeocacheListKind {
    NEARBY,
    HUNTS,
    FINDS,
    MINE,
}

/**
 * The hub's list tabs.
 *
 * Rows go straight to the shared card composables rather than through `ChannelCardCompose`:
 * `RenderGeocache` already exists for the feeds and produces exactly the card this list wants,
 * so routing it through the generic dispatcher would add a kind lookup to say the same thing.
 */
@Composable
fun RenderGeocacheFeed(
    feedContentState: FeedContentState,
    listState: LazyListState,
    kind: GeocacheListKind,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
        label = "RenderGeocacheFeed",
    ) { state ->
        when (state) {
            is FeedState.Empty -> GeocacheEmpty(kind)
            is FeedState.FeedError -> FeedError(state.errorMessage, feedContentState::invalidateData)
            is FeedState.Loading -> LoadingFeed()
            is FeedState.Loaded -> GeocacheFeedColumn(state, listState, kind, accountViewModel, nav)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GeocacheFeedColumn(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    kind: GeocacheListKind,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    // Only Nearby offers to sort by distance, and only when the reader asks. Collecting the
    // location flow is what switches the GPS on — it is SharingStarted.WhileSubscribed — so a
    // list that did it unprompted would spend a stranger's battery to reorder some rows.
    var useLocation by rememberSaveable(kind) { mutableStateOf(false) }

    val here =
        if (kind == GeocacheListKind.NEARBY && useLocation) {
            val fix by accountViewModel.account.geolocationFlow().collectAsStateWithLifecycle(null)
            (fix as? LocationResult.Success)?.geoHash
        } else {
            null
        }

    val ordered =
        remember(items.list, here) {
            if (here == null) {
                items.list
            } else {
                items.list.sortedBy { note ->
                    val cache = (note.event as? GeocacheListingEvent)?.geocachePoint()
                    // Caches with no usable geohash sink to the bottom rather than claiming
                    // distance zero and sitting at the top of a list about distance.
                    cache?.let { GeoRelayDirectory.haversineKm(here.centerLat, here.centerLon, it.first, it.second) }
                        ?: Double.MAX_VALUE
                }
            }
        }

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        if (kind == GeocacheListKind.NEARBY) {
            item(key = "geocache-sort-header") {
                DistanceSortHeader(
                    sorting = here != null,
                    requested = useLocation,
                    onRequest = { useLocation = true },
                )
            }
        }

        items(ordered, key = { it.idHex }) { item ->
            Column(Modifier.fillMaxWidth().animateItem()) {
                GeocacheFeedRow(item, kind, accountViewModel, nav)
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}

@Composable
private fun GeocacheFeedRow(
    note: Note,
    kind: GeocacheListKind,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (val event = note.event) {
        is GeocacheListingEvent ->
            Box(Modifier.clickable { nav.nav(Route.GeocacheDetail(event.address())) }) {
                RenderGeocache(note, accountViewModel)
            }

        is GeocacheFoundLogEvent ->
            Box(
                Modifier.clickable {
                    // A find's row leads to the cache, not to the log: "which cache was that?"
                    // is the question a finds list exists to answer.
                    event.geocache()?.let { nav.nav(Route.GeocacheDetail(it)) } ?: nav.nav(Route.Note(note.idHex))
                },
            ) {
                RenderGeocacheFoundLog(note, accountViewModel)
            }

        is GeocacheCurationListEvent ->
            GeocacheHuntRow(note, event, accountViewModel, nav)

        else -> Unit
    }
}

/**
 * The Nearby tab's ordering control.
 *
 * Reads as a statement of what the list is currently doing, with the opt-in as the action. Once
 * the reader has asked, the button is gone and the line simply says the order — a toggle they
 * have to keep re-reading would be worse than a fact.
 */
@Composable
private fun DistanceSortHeader(
    sorting: Boolean,
    requested: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                if (sorting) {
                    stringRes(Res.string.geocache_sorted_by_distance)
                } else {
                    stringRes(Res.string.geocache_sorted_by_recent)
                },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )

        if (!requested) {
            TextButton(onClick = onRequest) {
                Text(stringRes(Res.string.geocache_use_my_location))
            }
        }
    }
}

@Composable
private fun GeocacheEmpty(kind: GeocacheListKind) {
    val message =
        when (kind) {
            GeocacheListKind.NEARBY -> stringRes(Res.string.geocache_empty_nearby)
            GeocacheListKind.HUNTS -> stringRes(Res.string.geocache_empty_hunts)
            GeocacheListKind.FINDS -> stringRes(Res.string.geocache_empty_finds)
            GeocacheListKind.MINE -> stringRes(Res.string.geocache_empty_mine)
        }

    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
