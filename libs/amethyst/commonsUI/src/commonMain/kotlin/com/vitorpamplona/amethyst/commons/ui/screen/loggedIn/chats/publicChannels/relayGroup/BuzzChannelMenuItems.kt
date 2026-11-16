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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.buzz.ui.BuzzPinDropdownItem
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelStars
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.nip29RelayGroups.ui.RelayGroupMessagesDropdownItem
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * Pin/Unpin a Buzz channel (a local favorite of this account — [BuzzChannelStars]). Moved off the
 * per-channel list row into the opened channel's/forum's top-bar overflow, so the list row stays a
 * clean tap-to-open target. Reads the live starred set so the label + icon reflect the current state.
 */
@Composable
fun BuzzPinDropdownItem(
    groupId: GroupId,
    accountViewModel: AccountViewModel,
    closeMenu: () -> Unit,
) {
    val stars = accountViewModel.account.buzzChannelStars
    val starred by stars.flow.collectAsStateWithLifecycle()

    BuzzPinDropdownItem(
        isPinned = groupId.id in starred,
        closeMenu = closeMenu,
        onToggle = { stars.toggle(groupId.id) },
    )
}

/**
 * Add/Remove this relay-group [channel] from my kind-10009 list (whether it shows in Messages). A
 * reversible toggle that never touches my relay membership — same split as "Leave" — so it stays
 * readable either way. Reads the live kind-10009 list so it flips as the change lands. Moved off the
 * per-channel list row into the opened screen's top-bar overflow.
 */
@Composable
fun RelayGroupMessagesDropdownItem(
    channel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    closeMenu: () -> Unit,
) {
    val joinedGroupIds by accountViewModel.account.relayGroupList.liveRelayGroupIds
        .collectAsStateWithLifecycle()
    val onMyList = channel.groupId in joinedGroupIds

    RelayGroupMessagesDropdownItem(
        onMyList = onMyList,
        closeMenu = closeMenu,
        onToggle = {
            if (onMyList) {
                accountViewModel.removeRelayGroupFromMessages(channel)
            } else {
                accountViewModel.addRelayGroupToMessages(channel)
            }
        },
    )
}
