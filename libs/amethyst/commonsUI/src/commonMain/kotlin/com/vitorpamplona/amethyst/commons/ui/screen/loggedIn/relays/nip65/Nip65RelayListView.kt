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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip65

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relays.ui.RelayCountResult
import com.vitorpamplona.amethyst.commons.relays.ui.RelayDragState
import com.vitorpamplona.amethyst.commons.relays.ui.rememberRelayDragState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.rememberExtendedNav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.RelayUrlEditField
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HorzHalfVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

@Composable
fun Nip65RelayList(
    postViewModel: Nip65RelayListViewModel,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
    nav: INav,
) {
    val newNav = rememberExtendedNav(nav, onClose)

    val homeFeedState by postViewModel.homeRelays.collectAsStateWithLifecycle()
    val notifFeedState by postViewModel.notificationRelays.collectAsStateWithLifecycle()

    val homeDragState =
        rememberRelayDragState(
            onMove = { from, to -> postViewModel.moveHomeRelay(from, to) },
            itemCount = { homeFeedState.size },
        )
    val notifDragState =
        rememberRelayDragState(
            onMove = { from, to -> postViewModel.moveNotifRelay(from, to) },
            itemCount = { notifFeedState.size },
        )

    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyColumn(
            contentPadding = FeedPadding,
            userScrollEnabled = !homeDragState.isDragging && !notifDragState.isDragging,
        ) {
            renderNip65HomeItems(homeFeedState, postViewModel, accountViewModel, newNav, dragState = homeDragState)
            renderNip65NotifItems(notifFeedState, postViewModel, accountViewModel, newNav, dragState = notifDragState)
        }
    }
}

@Composable
fun Nip65InboxRelayList(
    postViewModel: Nip65RelayListViewModel,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
    nav: INav,
) {
    val newNav = rememberExtendedNav(nav, onClose)
    val notifFeedState by postViewModel.notificationRelays.collectAsStateWithLifecycle()
    val dragState =
        rememberRelayDragState(
            onMove = { from, to -> postViewModel.moveNotifRelay(from, to) },
            itemCount = { notifFeedState.size },
        )

    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyColumn(
            contentPadding = FeedPadding,
            userScrollEnabled = !dragState.isDragging,
        ) {
            renderNip65NotifItems(notifFeedState, postViewModel, accountViewModel, newNav, dragState = dragState)
        }
    }
}

@Composable
fun Nip65OutboxRelayList(
    postViewModel: Nip65RelayListViewModel,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
    nav: INav,
) {
    val newNav = rememberExtendedNav(nav, onClose)
    val homeFeedState by postViewModel.homeRelays.collectAsStateWithLifecycle()
    val dragState =
        rememberRelayDragState(
            onMove = { from, to -> postViewModel.moveHomeRelay(from, to) },
            itemCount = { homeFeedState.size },
        )

    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyColumn(
            contentPadding = FeedPadding,
            userScrollEnabled = !dragState.isDragging,
        ) {
            renderNip65HomeItems(homeFeedState, postViewModel, accountViewModel, newNav, dragState = dragState)
        }
    }
}

fun LazyListScope.renderNip65HomeItems(
    feedState: List<BasicRelaySetupInfo>,
    postViewModel: Nip65RelayListViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    countResults: Map<NormalizedRelayUrl, RelayCountResult> = emptyMap(),
    dragState: RelayDragState? = null,
) {
    itemsIndexed(feedState, key = { _, item -> "Nip65Home" + item.relay.url }) { index, item ->
        BasicRelaySetupInfoDialog(
            item,
            onDelete = { postViewModel.deleteHomeRelay(item) },
            nip11CachedRetriever = LocalCache.appHost.nip11Cache,
            modifier = HorzHalfVertPadding,
            countResult = countResults[item.relay],
            index = index,
            dragState = dragState,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    item {
        Spacer(modifier = StdVertSpacer)
        RelayUrlEditField(
            onNewRelay = { postViewModel.addHomeRelay(relaySetupInfoBuilder(it)) },
            modifier = HorzPadding,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

fun LazyListScope.renderNip65NotifItems(
    feedState: List<BasicRelaySetupInfo>,
    postViewModel: Nip65RelayListViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    countResults: Map<NormalizedRelayUrl, RelayCountResult> = emptyMap(),
    dragState: RelayDragState? = null,
) {
    itemsIndexed(feedState, key = { _, item -> "Nip65Notif" + item.relay.url }) { index, item ->
        BasicRelaySetupInfoDialog(
            item,
            onDelete = { postViewModel.deleteNotifRelay(item) },
            nip11CachedRetriever = LocalCache.appHost.nip11Cache,
            modifier = HorzHalfVertPadding,
            countResult = countResults[item.relay],
            index = index,
            dragState = dragState,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    item {
        Spacer(modifier = StdVertSpacer)
        RelayUrlEditField(
            onNewRelay = { postViewModel.addNotifRelay(relaySetupInfoBuilder(it)) },
            modifier = HorzPadding,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
