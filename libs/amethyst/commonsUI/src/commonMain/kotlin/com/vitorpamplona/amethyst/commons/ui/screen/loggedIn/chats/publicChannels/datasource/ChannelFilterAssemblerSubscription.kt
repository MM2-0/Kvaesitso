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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.relayClient.channel.ChannelFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.channel.ChannelQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun ChannelFilterAssemblerSubscription(
    channel: Channel,
    dataSource: ChannelFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    // different screens get different states
    // even if they are tracking the same tag.
    val state =
        remember(accountViewModel.account) {
            ChannelQueryState(channel, accountViewModel.account)
        }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)

    // Live streams need the 30311 event to populate `channel.info` before we can read its
    // `goal` tag and add the goal+zap subscriptions. Re-invalidate when metadata changes so
    // the goal filter fires as soon as the stream event arrives.
    if (channel is LiveActivitiesChannel) {
        val metadataState by channel
            .flow()
            .metadata.stateFlow
            .collectAsStateWithLifecycle()
        val goalId = channel.info?.goalEventId()
        LaunchedEffect(goalId, metadataState) {
            dataSource.invalidateFilters()
        }
    }

    // Relay groups: when the kind-39005 pin list changes, re-invalidate so the id-based back-fill
    // for pinned message bodies (see filterRelayGroupState) picks up the new ids. Keyed on the
    // pin list alone, so unrelated roster/metadata churn doesn't force a re-subscribe.
    if (channel is RelayGroupChannel) {
        val metadataState by channel
            .flow()
            .metadata.stateFlow
            .collectAsStateWithLifecycle()
        val pinnedIds = (metadataState.channel as? RelayGroupChannel)?.pins ?: channel.pins
        LaunchedEffect(pinnedIds) {
            dataSource.invalidateFilters()
        }
    }
}
