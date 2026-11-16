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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.BuzzDmJoinedChatTailQueryState
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupJoinedChatTailQueryState
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupJoinedStateQueryState
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupOpenChatHistoryFilterAssembler
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupOpenChatHistoryQueryState
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupOpenChatTailFilterAssembler
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupOpenChatTailQueryState
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmChannels
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.buildRelayGroupJoinedChatTailFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.KeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * Always-on preload of the relay-signed **state** (metadata/roster/roles/pins) of every joined group,
 * mounted once high in the logged-in tree ([com.vitorpamplona.amethyst.commons.ui.app.AmethystApp])
 * — the NIP-29 analog of the always-on account/DM tail and [ConcordChannelPreload]. The query state is
 * keyed on the account (stable), so we watch the joined-group list and re-derive on every join/leave.
 */
@Composable
fun RelayGroupJoinedStatePreload(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val dataSource = accountViewModel.dataSources().relayGroupJoinedState
    val state = remember(account) { RelayGroupJoinedStateQueryState(account) }

    val joined by account.relayGroupList.liveRelayGroupList.collectAsStateWithLifecycle()
    LaunchedEffect(joined) { dataSource.invalidateFilters() }

    KeyDataSourceSubscription(state, dataSource)
}

/**
 * Always-on preview **live tail** for joined groups' recent chat, mounted alongside [RelayGroupJoinedStatePreload]
 * — keeps the Messages-list previews reflecting the true newest message app-wide.
 *
 * Mounts **one subscription per joined group** (not one per host relay): a subscription whose `#h` carries
 * more than one group id is registered as a *global* subscription by `block/buzz` and then never receives
 * channel-scoped events, so a batched tail backfills once and goes deaf. See
 * [buildRelayGroupJoinedChatTailFilter]. Joining/leaving adds or removes a key, so nothing needs an
 * explicit invalidate; a group whose relay url won't normalize is skipped, exactly as the Messages feed
 * filter skips it.
 */
@Composable
fun RelayGroupJoinedChatTailPreload(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val dataSource = accountViewModel.dataSources().relayGroupJoinedChatTail

    val joined by account.relayGroupList.liveRelayGroupList.collectAsStateWithLifecycle()
    val enabledFeeds by account.chatFeedToggles.applied.collectAsStateWithLifecycle()

    if (ChatFeedType.NIP29 in enabledFeeds) {
        joined.forEach { tag ->
            val relay = RelayUrlNormalizer.normalizeOrNull(tag.relayUrl)
            if (relay != null) {
                val groupId = GroupId(tag.groupId, relay)
                key(groupId) {
                    val state = remember(account, groupId) { RelayGroupJoinedChatTailQueryState(account, groupId) }
                    KeyDataSourceSubscription(state, dataSource)
                }
            }
        }
    }
}

/**
 * Always-on preview **live tail** for the viewer's Buzz DM channels' recent chat, mounted alongside
 * [RelayGroupJoinedChatTailPreload] — keeps discovered DM conversations warm app-wide so a Buzz DM can
 * surface on the Notifications tab and in push without opening it. Re-derives whenever a DM is discovered
 * ([com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmChannels]) or hidden/unhidden
 * ([com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry]).
 */
@Composable
fun BuzzDmJoinedChatTailPreload(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val dataSource = accountViewModel.dataSources().buzzDmJoinedChatTail
    val me = account.userProfile().pubkeyHex

    // Both values must be read (not just collected) so the snapshot system recomposes this
    // preload — and re-derives the mounted set — whenever a DM is discovered or hidden.
    val channels by BuzzDmChannels.flow.collectAsStateWithLifecycle()
    val hidden by BuzzDmRegistry.hidden.collectAsStateWithLifecycle()

    val visible =
        remember(channels, hidden, me) {
            BuzzDmChannels.channelsFor(me).filterKeys { it !in BuzzDmRegistry.hiddenFor(me) }
        }

    // One subscription per DM channel — batching their ids into a single `#h` makes buzz treat the
    // subscription as global and stop delivering live messages. See [buildRelayGroupJoinedChatTailFilter].
    visible.forEach { (channelId, relay) ->
        val groupId = GroupId(channelId, relay)
        key(groupId) {
            val state = remember(account, groupId) { BuzzDmJoinedChatTailQueryState(account, groupId) }
            KeyDataSourceSubscription(state, dataSource)
        }
    }
}

/**
 * Mount on the open group chat screen to keep the *currently open* group's recent chat live — covers a
 * non-joined group opened by link (the batched preview tail is joined-only) and live updates. Lifecycle-
 * aware so it stops when the screen leaves.
 */
@Composable
fun RelayGroupOpenChatTailSubscription(
    groupId: GroupId,
    dataSource: RelayGroupOpenChatTailFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    val state = remember(account, groupId) { RelayGroupOpenChatTailQueryState(account, groupId) }
    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}

/**
 * Mount on the open group chat screen to keep its backward-history pager bound and armed (older
 * kind-9/poll by `until`+`limit` on the host relay), the NIP-29 analog of [ConcordChannelHistorySubscription].
 */
@Composable
fun RelayGroupOpenChatHistorySubscription(
    groupId: GroupId,
    dataSource: RelayGroupOpenChatHistoryFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    val state = remember(account, groupId) { RelayGroupOpenChatHistoryQueryState(account, groupId) }
    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}
