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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geohash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.geocaches.dal.GeocachesHereFeedViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.GeocacheTab
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_caches_here
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.rememberGeocachePalette
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.datasource.GeocachesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * "N caches here", shown on a geohash feed when there are any.
 *
 * A player scrolling a place's feed is exactly the person who wants to know there is something
 * hidden in it, and the geohash screen is where Amethyst already answers "what is going on
 * here". The chip is absent rather than zeroed when the cell holds nothing, because a permanent
 * "0 caches here" is noise on every other place in the world.
 *
 * Counted through a feed filter rather than a one-shot scan of LocalCache. The chip mounts the
 * shared geocaching REQ, so a cell the reader has never visited fills in while they sit on it —
 * a plain scan showed nothing and stayed at nothing, which read as "no caches here" when the
 * truth was "nothing fetched yet".
 */
@Composable
fun GeocachesHereChip(
    geohash: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val model: GeocachesHereFeedViewModel =
        rememberViewModel(
            key = geohash + "GeocachesHereFeedViewModel",
            factory = GeocachesHereFeedViewModel.Factory(geohash, accountViewModel.account),
        )

    WatchLifecycleAndUpdateModel(model)

    // The shared geocaching REQ. Without it the chip can only ever report caches some other
    // screen happened to fetch first.
    GeocachesFilterAssemblerSubscription(accountViewModel)

    val feedState by model.feedState.feedContent.collectAsStateWithLifecycle()

    when (val state = feedState) {
        is FeedState.Loaded -> GeocachesHereCount(state, nav)
        else -> Unit
    }
}

@Composable
private fun GeocachesHereCount(
    state: FeedState.Loaded,
    nav: INav,
) {
    val palette = rememberGeocachePalette()
    val loaded by state.feed.collectAsStateWithLifecycle()
    val count = loaded.list.size

    if (count == 0) return

    Row(
        modifier =
            Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(palette.wash(palette.live))
                .clickable { nav.nav(Route.Geocaches(GeocacheTab.MAP)) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Explore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = palette.live,
        )
        Text(
            text = pluralStringRes(Res.plurals.geocache_caches_here, count, count),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
