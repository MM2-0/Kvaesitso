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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.amethyst.commons.relays.ui.RelayCountResult
import com.vitorpamplona.amethyst.commons.relays.ui.RelayDragState
import com.vitorpamplona.amethyst.commons.relays.ui.RelayEventCountRow
import com.vitorpamplona.amethyst.commons.relays.ui.draggableRelayItem
import com.vitorpamplona.amethyst.commons.relays.ui.relayDragHandle
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.and_more
import com.vitorpamplona.amethyst.commons.resources.relay_reorder
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHalfVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Height25Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.LargeRelayIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowHeightChatMaxWidth
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BasicRelaySetupInfoClickableRow(
    item: BasicRelaySetupInfo,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    onDelete: ((BasicRelaySetupInfo) -> Unit)?,
    onClick: () -> Unit,
    nip11CachedRetriever: Nip11CachedRetriever,
    modifier: Modifier = Modifier,
    index: Int = -1,
    dragState: RelayDragState? = null,
    countResult: RelayCountResult? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val itemModifier =
        if (dragState != null && index >= 0) {
            Modifier
                .fillMaxWidth()
                .draggableRelayItem(index, dragState)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        scope.launch {
                            clipboardManager.setText(item.relay.url)
                        }
                    },
                )
        } else {
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        scope.launch {
                            clipboardManager.setText(item.relay.url)
                        }
                    },
                )
        }

    Column(itemModifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            if (dragState != null && index >= 0) {
                val handleModifier = Modifier.height(24.dp).relayDragHandle(index, dragState)
                Icon(
                    MaterialSymbols.DragIndicator,
                    contentDescription = stringRes(Res.string.relay_reorder),
                    modifier = handleModifier,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val iconUrlFromRelayInfoDoc by loadRelayInfo(item.relay, nip11CachedRetriever)

            RenderRelayIcon(
                iconUrlFromRelayInfoDoc.id ?: item.relay.displayUrl(),
                iconUrlFromRelayInfoDoc.icon,
                loadProfilePicture,
                loadRobohash,
                item.relayStat.pingInMs,
                LargeRelayIconModifier,
            )

            Spacer(modifier = HalfHorzPadding)

            Column(Modifier.weight(1f)) {
                RelayNameAndRemoveButton(
                    item,
                    onClick,
                    onDelete,
                    ReactionRowHeightChatMaxWidth,
                )

                UsedBy(item, accountViewModel, nav)

                RelayEventCountRow(
                    countResult = countResult,
                    modifier = ReactionRowHeightChatMaxWidth,
                )

                RelayStatusRow(
                    item = item,
                    onClick = onClick,
                    modifier = ReactionRowHeightChatMaxWidth,
                    accountViewModel = accountViewModel,
                )

                RelayLatencyLine(
                    relay = item.relay,
                    store = accountViewModel.host.relayHealth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        HorizontalDivider(thickness = DividerThickness)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsedBy(
    item: BasicRelaySetupInfo,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (item.users.isNotEmpty()) {
        Row(modifier = HalfHalfVertPadding, verticalAlignment = Alignment.CenterVertically) {
            item.users.getOrNull(0)?.let {
                UserPicture(
                    user = it,
                    size = Size25dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
            item.users.getOrNull(1)?.let {
                UserPicture(
                    user = it,
                    size = Size25dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
            item.users.getOrNull(2)?.let {
                UserPicture(
                    user = it,
                    size = Size25dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
            item.users.getOrNull(3)?.let {
                UserPicture(
                    user = it,
                    size = Size25dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
            if (item.users.size > 4) {
                Box(contentAlignment = Alignment.Center, modifier = Height25Modifier) {
                    Text(
                        text = stringRes(Res.string.and_more, item.users.size - 4),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
