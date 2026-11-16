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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.connected

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.active_subs_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.rememberExtendedNav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HorzHalfVertPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun ConnectedRelayList(
    postViewModel: ConnectedRelayListViewModel,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
    nav: INav,
) {
    val newNav = rememberExtendedNav(nav, onClose)
    val feedState by postViewModel.relays.collectAsStateWithLifecycle()

    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyColumn(
            contentPadding = FeedPadding,
        ) {
            renderConnectedItems(feedState, postViewModel, accountViewModel, newNav)
        }
    }
}

fun LazyListScope.renderConnectedItems(
    feedState: List<BasicRelaySetupInfo>,
    postViewModel: ConnectedRelayListViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The list answers "which relays am I on"; this answers the follow-up question it always
    // provokes — "and why are there this many".
    item {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.nav(Route.ActiveSubscriptions) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringRes(Res.string.active_subs_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider()
    }

    itemsIndexed(feedState, key = { _, item -> "Connected" + item.relay.url }) { _, item ->
        BasicRelaySetupInfoDialog(
            item,
            onDelete = null,
            nip11CachedRetriever = LocalCache.appHost.nip11Cache,
            modifier = HorzHalfVertPadding,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
