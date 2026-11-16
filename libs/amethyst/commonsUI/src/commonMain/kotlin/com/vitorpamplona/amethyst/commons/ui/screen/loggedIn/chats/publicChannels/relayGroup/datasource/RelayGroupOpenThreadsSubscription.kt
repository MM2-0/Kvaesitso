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
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupOpenThreadsHistoryFilterAssembler
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupOpenThreadsHistoryQueryState
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.RelayGroupOpenThreadsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.RelayGroupOpenThreadsQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/** Mount on a group's Threads screen to stream its kind-11 threads + 1111 comments (the recent live tail). */
@Composable
fun RelayGroupOpenThreadsSubscription(
    channel: RelayGroupChannel,
    dataSource: RelayGroupOpenThreadsFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    val state =
        remember(channel.groupId) {
            RelayGroupOpenThreadsQueryState(channel)
        }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}

/**
 * Mount on a group's Threads screen to keep its backward-history pager bound and armed (older kind-11/1111
 * by `until`+`limit` on the host relay), the Threads analog of [RelayGroupOpenChatHistorySubscription].
 */
@Composable
fun RelayGroupOpenThreadsHistorySubscription(
    groupId: GroupId,
    dataSource: RelayGroupOpenThreadsHistoryFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    val state = remember(account, groupId) { RelayGroupOpenThreadsHistoryQueryState(account, groupId) }
    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}
