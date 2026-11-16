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
package com.vitorpamplona.amethyst.commons.relayClient.channel

import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

class ChannelFromUserFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChannelQueryState>,
) : PerUserAndFollowListEoseManager<ChannelQueryState, Channel>(client, allKeys) {
    override fun updateFilter(
        key: ChannelQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? =
        when (val channel = key.channel) {
            is EphemeralChatChannel -> filterMyMessagesToEphemeralChat(channel, userHex(key), since)
            is PublicChatChannel -> filterMyMessagesToPublicChat(channel, user(key).pubkeyHex, since)
            is LiveActivitiesChannel -> filterMyMessagesToLiveActivities(channel, userHex(key), since)
            // A relay group's timeline is served by the group content tail + history pager (both
            // all-authors `#h` on the single host relay), which already return my own messages — so a
            // dedicated `authors=[me]` reconciliation filter is redundant here. See
            // amethyst/plans/2026-07-18-nip29-group-chat-subscriptions.md.
            is RelayGroupChannel -> emptyList()
            else -> null
        }

    fun userHex(key: ChannelQueryState) = key.account.userProfile().pubkeyHex

    override fun user(key: ChannelQueryState) = key.account.userProfile()

    override fun list(key: ChannelQueryState) = key.channel
}
