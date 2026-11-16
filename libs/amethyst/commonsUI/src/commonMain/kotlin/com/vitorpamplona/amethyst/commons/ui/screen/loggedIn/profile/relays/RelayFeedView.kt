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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.relays

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.private_inbox_section
import com.vitorpamplona.amethyst.commons.resources.private_inbox_section_explainer_profile
import com.vitorpamplona.amethyst.commons.resources.public_home_section
import com.vitorpamplona.amethyst.commons.resources.public_home_section_explainer_profile
import com.vitorpamplona.amethyst.commons.resources.public_notif_section
import com.vitorpamplona.amethyst.commons.resources.public_notif_section_explainer_profile
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.RelayCompose
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.MyRelayInfo
import com.vitorpamplona.amethyst.commons.viewmodels.RelayFeedViewModel
import kotlinx.coroutines.launch

@Composable
fun RelayFeedView(
    viewModel: RelayFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val outboxListState by viewModel.nip65OutboxFlow.collectAsStateWithLifecycle()
    val inboxListState by viewModel.nip65InboxFlow.collectAsStateWithLifecycle()
    val dmListState by viewModel.dmInboxFlow.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp),
        state = rememberLazyListState(),
    ) {
        item {
            SettingsCategory(
                Res.string.public_home_section,
                Res.string.public_home_section_explainer_profile,
                Modifier.padding(top = 10.dp, bottom = 8.dp, start = 10.dp, end = 10.dp),
            )
        }
        itemsIndexed(outboxListState, key = { _, item -> "outbox" + item.url.url }) { _, item ->
            RenderRelayRow(item, accountViewModel, nav)
        }
        item {
            SettingsCategory(
                Res.string.public_notif_section,
                Res.string.public_notif_section_explainer_profile,
                Modifier.padding(top = 24.dp, bottom = 8.dp, start = 10.dp, end = 10.dp),
            )
        }
        itemsIndexed(inboxListState, key = { _, item -> "inbox" + item.url.url }) { _, item ->
            RenderRelayRow(item, accountViewModel, nav)
        }
        item {
            SettingsCategory(
                Res.string.private_inbox_section,
                Res.string.private_inbox_section_explainer_profile,
                Modifier.padding(top = 24.dp, bottom = 8.dp, start = 10.dp, end = 10.dp),
            )
        }
        itemsIndexed(dmListState, key = { _, item -> "dminbox" + item.url.url }) { _, item ->
            RenderRelayRow(item, accountViewModel, nav)
        }
    }
}

@Composable
private fun RenderRelayRow(
    relay: MyRelayInfo,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    RelayCompose(
        relay,
        accountViewModel = accountViewModel,
        onAddRelay = {
            scope.launch {
                clipboardManager.setText(relay.url.url)
                nav.nav(Route.EditRelays)
            }
        },
        onRemoveRelay = {
            nav.nav(Route.EditRelays)
        },
        onClick = {
            nav.nav(Route.RelayInfo(relay.url.url))
        },
    )
    HorizontalDivider(
        thickness = DividerThickness,
    )
}
